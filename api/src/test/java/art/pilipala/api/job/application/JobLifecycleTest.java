package art.pilipala.api.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import art.pilipala.api.billing.application.CreditGrantService;
import art.pilipala.api.billing.application.JobCostPolicy;
import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.BillingProperties;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.billing.support.LedgerAssertions;
import art.pilipala.api.i18n.InMemoryI18nMessageRepository;
import art.pilipala.api.i18n.application.I18nMessageService;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.storage.TosProperties;
import art.pilipala.api.storage.TosUrlSigner;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobMessageTransport;
import art.pilipala.api.job.infra.FakeJobTransport;
import art.pilipala.api.job.infra.JobRequestHasher;
import art.pilipala.api.job.support.FakeJobLease;
import art.pilipala.api.job.support.InMemoryJobStore;
import art.pilipala.api.job.support.InMemoryModelCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Job 域的状态机与幂等：成功、重试回 queued、取消、dead。
 *
 * <p>用内存仓储 + FakeTransport 跑，不依赖 Postgres / Redis / RabbitMQ；
 * 仓储语义与 JDBC 实现一致（见 {@link InMemoryJobStore}）。
 *
 * <p>WP6 起任务与钱包联动：提交先冻结，succeeded 确认实扣，
 * cancelled / dead 解冻退回，重试期间冻结保持不动。
 */
class JobLifecycleTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final String WORKER = "worker-1";
    private static final long COST = 10;

    private final InMemoryJobStore store = new InMemoryJobStore();
    private final InMemoryWalletStore wallets = new InMemoryWalletStore(store::statusOf);
    private final InMemoryModelCatalogRepository catalogRepository = new InMemoryModelCatalogRepository();
    private final ModelCatalog modelCatalog = new ModelCatalog(catalogRepository);
    private final FakeJobTransport transport = new FakeJobTransport();
    private final FakeJobLease lease = new FakeJobLease();
    private final ObjectMapper mapper = new ObjectMapper();
    private final BillingProperties billing = new BillingProperties(COST, 0, 200, 50);

    private final CreditGrantService grantService = new CreditGrantService(new InMemoryCreditGrantStore(), wallets, wallets);
    private final WalletService walletService = new WalletService(wallets, wallets, wallets, grantService);
    private final HoldSettlement holdSettlement = new HoldSettlement(walletService);

    private final InMemoryI18nMessageRepository i18nRepository = new InMemoryI18nMessageRepository();
    private final I18nMessageService i18n = new I18nMessageService(i18nRepository);

    private final SubmitJob submitJob = new SubmitJob(store, store, store, new JobRequestHasher(), walletService,
            new JobCostPolicy(billing), modelCatalog);
    private final GetJob getJob = new GetJob(store, holdSettlement, i18n);
    private final TosUrlSigner tosUrlSigner = new TosUrlSigner(new TosProperties("stub", "http://127.0.0.1:18080",
            "cn-beijing", "pilipala-dev", "pilipala-local-ak", "pilipala-local-sk", 900, 15000, "/tmp/pilipala-tos"));
    private final GetJobResult getJobResult = new GetJobResult(store, mapper, holdSettlement, tosUrlSigner, i18n);
    private final CancelJob cancelJob = new CancelJob(store, holdSettlement, i18n);
    private final ClaimJob claimJob = new ClaimJob(store, store, lease, holdSettlement);
    private final RecordAttemptResult recordAttemptResult =
            new RecordAttemptResult(store, store, store, lease, mapper, holdSettlement);
    private final OutboxDispatchTick dispatchTick = new OutboxDispatchTick(store, transport, 50, 10);

    @BeforeEach
    void topUpTheTestUser() {
        walletService.credit(USER, 100);
    }

    @Test
    void submitCreatesQueuedJobWithOutboxAndIdempotencyRecord() throws Exception {
        SubmitJobView view = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);

        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.holdId()).isNotBlank();
        assertThat(store.allJobs()).hasSize(1);
        assertThat(store.allOutbox()).hasSize(1);
        assertThat(store.findByUserAndKey(USER, "k1")).isPresent();

        JsonNode payload = mapper.readTree(store.allOutbox().get(0).payloadJson());
        assertThat(payload.get("job_id").asText()).isEqualTo(view.jobId());
        assertThat(payload.has("event_id")).isTrue();
        assertThat(payload.has("params")).isFalse();
        // 队列消息里没有钱包金额与 hold_id（ADR 0003）。
        assertThat(payload.has("hold_id")).isFalse();
    }

    @Test
    void submitFreezesTheWallet() {
        SubmitJobView view = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);

        assertThat(wallets.walletOf(USER).available()).isEqualTo(90);
        assertThat(wallets.walletOf(USER).frozen()).isEqualTo(COST);

        WalletHold hold = wallets.allHolds().get(0);
        assertThat(hold.id().toString()).isEqualTo(view.holdId());
        assertThat(hold.status()).isEqualTo(WalletHoldStatus.FROZEN);
        assertThat(hold.jobId().toString()).isEqualTo(view.jobId());

        WalletLedgerEntry freeze = wallets.findLedgerByUserId(USER).get(1);
        assertThat(freeze.type()).isEqualTo(WalletLedgerType.FREEZE);
        assertThat(freeze.amount()).isEqualTo(COST);
        LedgerAssertions.assertInvariants(wallets, USER);
    }

    @Test
    void submitIsRejectedWhenTheBalanceIsNotEnough() {
        assertThatThrownBy(() -> submitJob.submit(OTHER_USER, "k1", "image.generate", "demo", params("hi"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("WALLET_INSUFFICIENT"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(store.allOutbox()).isEmpty();
        assertThat(store.findByUserAndKey(OTHER_USER, "k1")).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
        assertThat(wallets.findLedgerByUserId(OTHER_USER)).isEmpty();
        assertThat(wallets.walletOf(OTHER_USER).available()).isZero();
        assertThat(wallets.walletOf(OTHER_USER).frozen()).isZero();
    }

    @Test
    void sameKeySameBodyReplaysTheSameJobWithoutRepublishing() {
        SubmitJobView first = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);
        SubmitJobView second = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);

        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.holdId()).isEqualTo(first.holdId());
        assertThat(store.allJobs()).hasSize(1);
        assertThat(store.allOutbox()).hasSize(1);
        // 重放不二次冻结。
        assertThat(wallets.walletOf(USER).available()).isEqualTo(90);
        assertThat(wallets.walletOf(USER).frozen()).isEqualTo(COST);
        assertThat(wallets.allHolds()).hasSize(1);
        assertThat(wallets.findLedgerByUserId(USER)).hasSize(2);
    }

    @Test
    void sameKeyDifferentBodyIsAConflict() {
        submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);

        assertThatThrownBy(() -> submitJob.submit(USER, "k1", "image.generate", "demo", params("别的"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(store.allJobs()).hasSize(1);
    }

    @Test
    void otherUsersJobIsNotFound() {
        SubmitJobView view = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);
        UUID jobId = UUID.fromString(view.jobId());

        assertThatThrownBy(() -> getJob.get(OTHER_USER, jobId, "zh"))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("JOB_NOT_FOUND"));
        assertThatThrownBy(() -> getJobResult.result(OTHER_USER, jobId, "zh"))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> cancelJob.cancel(OTHER_USER, jobId, "zh"))
                .isInstanceOf(JobException.class);
    }

    @Test
    void claimThenSucceedMakesTheResultVisibleAndConfirmsTheHold() {
        UUID jobId = submit("k1");

        ClaimResult claim = claimJob.claim(jobId, WORKER);
        assertThat(claim.claimed()).isTrue();
        assertThat(claim.status()).isEqualTo("running");
        assertThat(lease.heldCount()).isEqualTo(1);

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png")));
        assertThat(record.status()).isEqualTo("succeeded");

        JobView view = getJob.get(USER, jobId, "zh");
        assertThat(view.status()).isEqualTo("succeeded");
        assertThat(view.attempt()).isEqualTo(1);
        assertThat(view.errorCode()).isNull();

        JobResultView result = getJobResult.result(USER, jobId, "zh");
        assertThat(result.outputs()).hasSize(1);
        assertThat(result.outputs().get(0).objectKey()).isEqualTo("img/a.png");
        assertThat(result.outputs().get(0).url()).isNotNull();
        assertThat(result.outputs().get(0).url()).contains("X-Tos-Algorithm=TOS4-HMAC-SHA256");
        assertThat(result.outputs().get(0).url()).contains("X-Tos-Signature=");
        assertThat(lease.heldCount()).isZero();

        assertThat(wallets.walletOf(USER).available()).isEqualTo(90);
        assertThat(wallets.walletOf(USER).frozen()).isZero();
        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.CONFIRMED);
        assertThat(wallets.findLedgerByUserId(USER).get(2).type()).isEqualTo(WalletLedgerType.CONFIRM);
        LedgerAssertions.assertInvariants(wallets, USER);
    }

    @Test
    void failedAttemptGoesBackToQueuedAndIsPublishedAgain() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));

        assertThat(record.status()).isEqualTo("queued");
        JobView view = getJob.get(USER, jobId, "zh");
        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.attempt()).isEqualTo(1);
        assertThat(view.errorCode()).isEqualTo("JOB_FAILED");

        assertThat(store.allOutbox()).hasSize(2);
        assertThat(dispatchTick.tick()).isEqualTo(2);
        assertThat(transport.sent()).hasSize(2);
        assertThat(store.allOutbox()).allMatch(message -> "sent".equals(message.status()));
    }

    /** WP9：改了仓储里的模板后，任务查询返回的文案随之动态变化。 */
    @Test
    void theMessageFollowsTheTemplateInTheRepository() {
        UUID jobId = submit("k1");
        claimJob.claim(jobId, WORKER);
        recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));

        assertThat(getJob.get(USER, jobId, "zh").message()).isEqualTo("任务执行失败。");

        i18nRepository.upsert("JOB_FAILED", "zh", "生成失败了，请稍后再试。");

        assertThat(getJob.get(USER, jobId, "zh").message()).isEqualTo("生成失败了，请稍后再试。");
        assertThat(getJobResult.result(USER, jobId, "zh").message()).isEqualTo("生成失败了，请稍后再试。");
    }

    @Test
    void retryKeepsTheHoldFrozen() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));

        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.FROZEN);
        assertThat(wallets.walletOf(USER).frozen()).isEqualTo(COST);
        assertThat(wallets.walletOf(USER).available()).isEqualTo(90);
        assertThat(wallets.findLedgerByUserId(USER)).hasSize(2);
        LedgerAssertions.assertInvariants(wallets, USER);
    }

    @Test
    void attemptsExhaustedBecomeDeadAndReleaseTheHold() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("queued");
        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("queued");
        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("dead");

        JobView view = getJob.get(USER, jobId, "zh");
        assertThat(view.status()).isEqualTo("dead");
        assertThat(view.attempt()).isEqualTo(3);
        assertThat(view.maxAttempts()).isEqualTo(3);
        assertThat(view.errorCode()).isEqualTo("JOB_FAILED");

        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(USER).frozen()).isZero();
        assertThat(wallets.walletOf(USER).available()).isEqualTo(100);
        assertThat(wallets.findLedgerByUserId(USER).get(2).type()).isEqualTo(WalletLedgerType.RELEASE);
        LedgerAssertions.assertInvariants(wallets, USER);
    }

    @Test
    void cancellingAQueuedJobIsImmediateAndIdempotent() {
        UUID jobId = submit("k1");

        JobView cancelled = cancelJob.cancel(USER, jobId, "zh");
        assertThat(cancelled.status()).isEqualTo("cancelled");
        assertThat(cancelled.errorCode()).isEqualTo("JOB_CANCELLED");
        assertThat(cancelled.message()).isEqualTo("任务已取消。");

        JobView again = cancelJob.cancel(USER, jobId, "zh");
        assertThat(again.status()).isEqualTo("cancelled");
        assertThat(again.errorCode()).isEqualTo("JOB_CANCELLED");

        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(USER).available()).isEqualTo(100);
        assertThat(wallets.walletOf(USER).frozen()).isZero();
        // 重放不写第二条 release 流水。
        assertThat(wallets.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(wallets, USER);
    }

    @Test
    void cancellingARunningJobKeepsTheIntentUntilTheAttemptReports() {
        UUID jobId = submit("k1");
        claimJob.claim(jobId, WORKER);

        JobView requested = cancelJob.cancel(USER, jobId, "zh");
        assertThat(requested.status()).isEqualTo("running");

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));
        assertThat(record.status()).isEqualTo("cancelled");
        assertThat(getJob.get(USER, jobId, "zh").errorCode()).isEqualTo("JOB_CANCELLED");
        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(USER).available()).isEqualTo(100);
        LedgerAssertions.assertInvariants(wallets, USER);
    }

    /** WP5 收口项：运行期间用户取消，即使这次 attempt 成功也收敛为 cancelled。 */
    @Test
    void cancellingARunningJobOverridesASuccessfulAttempt() {
        UUID jobId = submit("k1");
        claimJob.claim(jobId, WORKER);

        cancelJob.cancel(USER, jobId, "zh");

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png")));
        assertThat(record.status()).isEqualTo("cancelled");

        JobView view = getJob.get(USER, jobId, "zh");
        assertThat(view.status()).isEqualTo("cancelled");
        assertThat(view.errorCode()).isEqualTo("JOB_CANCELLED");
        // 取消的 attempt 不写结果
        assertThat(getJobResult.result(USER, jobId, "zh").outputs()).isEmpty();
        // 取消走的是解冻，不是实扣。
        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(USER).available()).isEqualTo(100);
        LedgerAssertions.assertInvariants(wallets, USER);
    }

    @Test
    void illegalTransitionIsRejected() {
        UUID jobId = submit("k1");

        assertThatThrownBy(() -> recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png"))))
                .isInstanceOf(JobException.class);
        assertThat(getJob.get(USER, jobId, "zh").status()).isEqualTo("queued");
    }

    @Test
    void outboxDeliveryFailureKeepsPendingUntilTheLimitIsReached() {
        UUID jobId = submit("k1");
        JobMessageTransport broken = message -> {
            throw new IllegalStateException("broker down");
        };
        OutboxDispatchTick failing = new OutboxDispatchTick(store, broken, 50, 3);

        assertThat(failing.tick()).isZero();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("pending");
        failing.tick();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("pending");
        failing.tick();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("failed");
        assertThat(jobId).isNotNull();
    }

    @Test
    void productSubmitUsesProductPriceAndGatewayGroup() {
        ModelProduct product = product("video-stable", 30, true);
        catalogRepository.product(product)
                .group(new ModelProductGroup(UUID.randomUUID(), product.id(), "default", "sd-2-c4",
                        10, 900, new BigDecimal("2.0"), true));

        SubmitJobView view = submitJob.submit(USER, "video-k", "video.generate", product.code(), params("hi"), null);

        Job job = store.findById(UUID.fromString(view.jobId())).orElseThrow();
        assertThat(job.productId()).isEqualTo(product.id());
        assertThat(job.targetId()).isNull();
        assertThat(job.gatewayGroup()).isEqualTo("default");
        assertThat(job.gatewayModel()).isEqualTo("sd-2-c4");
        assertThat(job.gatewayTimeoutSeconds()).isEqualTo(900);
        assertThat(job.quotedPrice()).isEqualTo(30);
        assertThat(job.pricingVersion()).isEqualTo("wp21-v1");
        assertThat(job.model()).isEqualTo("video-stable");
        assertThat(wallets.walletOf(USER).available()).isEqualTo(70);
        assertThat(wallets.walletOf(USER).frozen()).isEqualTo(30);
        assertThat(wallets.allHolds()).hasSize(1);
    }

    @Test
    void disabledProductDoesNotFallBackToLegacyPricing() {
        ModelProduct product = product("video-disabled", 30, false);
        catalogRepository.product(product);

        assertThatThrownBy(() -> submitJob.submit(USER, "video-k", "video.generate", product.code(), params("hi"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
    }

    @Test
    void productWithoutUsableGroupIsNoAvailableChannel() {
        ModelProduct product = product("video-no-group", 30, true);
        catalogRepository.product(product);

        assertThatThrownBy(() -> submitJob.submit(USER, "video-k", "video.generate", product.code(), params("hi"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("NO_AVAILABLE_CHANNEL"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
    }

    @Test
    void productSubmitWithoutPromptIsValidationError() {
        ModelProduct product = product("video-no-prompt", 30, true);
        catalogRepository.product(product);

        assertThatThrownBy(() -> submitJob.submit(USER, "video-k", "video.generate", product.code(),
                mapper.createObjectNode(), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));
        assertThat(store.allJobs()).isEmpty();
    }

    private UUID submit(String key) {
        return UUID.fromString(submitJob.submit(USER, key, "image.generate", "demo", params("hi"), null).jobId());
    }

    private com.fasterxml.jackson.databind.JsonNode params(String prompt) {
        return mapper.createObjectNode().put("prompt", prompt);
    }

    private ModelProduct product(String code, long price, boolean enabled) {
        return new ModelProduct(UUID.randomUUID(), code, "video.generate", "Video", "test", price, "{}", enabled, 10);
    }


}
