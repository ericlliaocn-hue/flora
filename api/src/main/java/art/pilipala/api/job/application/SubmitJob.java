package art.pilipala.api.job.application;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import art.pilipala.api.billing.application.JobCostPolicy;
import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.job.domain.IdempotencyRecord;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.domain.JobTransitions;
import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.infra.IdempotencyRepository;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobMessagePayload;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.job.infra.JobRequestHasher;
import art.pilipala.api.job.infra.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提交一个托管任务。
 *
 * <p>事务顺序（brief 锁定的「顺序 A」）：冻结钱包 → 写 job(created, hold_id) → 迁到 queued
 * → 绑定冻结单的 job_id → 写 outbox(pending) → 写幂等记录；事务提交后由 dispatcher 异步投递。
 *
 * <p>同 key 同 body 重放 → 返回原 job_id 与原 hold_id，不新建 job、不二次冻结；
 * 同 key 不同 body → IDEMPOTENCY_CONFLICT。
 *
 * <p>余额不足时冻结先失败，此时 job / outbox / 幂等记录一行都没写，
 * 用户充值后可用同一个 idempotency_key 重新提交。
 */
@Service
public class SubmitJob {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_CAPABILITY_LENGTH = 64;
    private static final int MAX_MODEL_LENGTH = 128;
    /** WP21：产品价快照的定价版本；产品改价时递增。 */
    private static final String PRICING_VERSION = "wp21-v1";

    private final JobRepository jobs;
    private final OutboxRepository outbox;
    private final IdempotencyRepository idempotency;
    private final JobRequestHasher hasher;
    private final WalletService wallets;
    private final JobCostPolicy costPolicy;
    private final ModelCatalog catalog;

    public SubmitJob(JobRepository jobs, OutboxRepository outbox, IdempotencyRepository idempotency, JobRequestHasher hasher,
            WalletService wallets, JobCostPolicy costPolicy, ModelCatalog catalog) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.hasher = hasher;
        this.wallets = wallets;
        this.costPolicy = costPolicy;
        this.catalog = catalog;
    }

    @Transactional
    public SubmitJobView submit(UUID userId, String idempotencyKey, String capability, String model, JsonNode params, String clientJobId) {
        String key = requireText(idempotencyKey, MAX_IDEMPOTENCY_KEY_LENGTH);
        String capabilityText = requireText(capability, MAX_CAPABILITY_LENGTH);
        String modelText = requireText(model, MAX_MODEL_LENGTH);
        if (params == null || !params.isObject()) {
            throw JobException.validation();
        }
        // client_job_id 只是客户端追踪用的，本 WP 不落库（要落库得先改 data-model）。
        String hash = hasher.hash(capabilityText, modelText, params);

        Optional<IdempotencyRecord> recorded = idempotency.findByUserAndKey(userId, key);
        if (recorded.isPresent()) {
            return replay(recorded.get(), hash);
        }

        // 产品路径：按产品价冻结、写产品/首个 target；老 model 名仍走 legacy 价格。
        Optional<ModelProduct> product = catalog.findProduct(modelText);
        long cost;
        UUID productId = null;
        UUID targetId = null;
        String gatewayGroup = null;
        String gatewayModel = null;
        Integer gatewayTimeoutSeconds = null;
        Long quotedPrice = null;
        String pricingVersion = null;
        if (product.isPresent()) {
            ModelProduct resolvedProduct = product.get();
            if (!resolvedProduct.enabled()
                    || !resolvedProduct.capability().equalsIgnoreCase(capabilityText)
                    || params.path("prompt").asText("").isBlank()) {
                throw JobException.validation();
            }
            ModelCatalog.ResolvedProduct route = catalog.resolve(resolvedProduct);
            ModelCatalog.ResolvedGroup group = catalog.resolveGroup(resolvedProduct);
            productId = resolvedProduct.id();
            targetId = route.primary().target().id();
            gatewayGroup = group.group().groupCode();
            gatewayModel = group.group().modelCode();
            gatewayTimeoutSeconds = group.group().timeoutSeconds();
            quotedPrice = resolvedProduct.priceCredits();
            pricingVersion = PRICING_VERSION;
            cost = resolvedProduct.priceCredits();
        } else {
            cost = costPolicy.costOf(capabilityText, modelText);
        }
        // 冻结：余额不足直接抛 WALLET_INSUFFICIENT，此时还没写 job。
        WalletHold hold = wallets.freeze(userId, cost);

        Optional<Job> created = jobs.insertIfAbsent(userId, capabilityText, modelText, productId, targetId,
                gatewayGroup, gatewayModel, gatewayTimeoutSeconds, quotedPrice, pricingVersion, params.toString(),
                key, hold.id());
        if (created.isEmpty()) {
            // 并发同键：另一个事务已经写好 job 与幂等记录。
            // 本次冻结要退回（冻结单 + release 流水），再重放对方的结果。
            wallets.release(hold.id());
            IdempotencyRecord record = idempotency.findByUserAndKey(userId, key).orElseThrow(JobException::idempotencyConflict);
            return replay(record, hash);
        }

        Job job = created.get();
        JobTransitions.requireQueued(job.status());
        if (!jobs.updateStatus(job.id(), JobStatus.CREATED, JobStatus.QUEUED, null)) {
            throw JobException.validation();
        }
        wallets.bindJob(hold.id(), job.id());
        UUID eventId = UUID.randomUUID();
        outbox.insertPending(eventId, "job", job.id(), JobMessagePayload.of(job.id(), eventId));
        idempotency.insert(userId, key, job.id(), hash);
        return new SubmitJobView(job.id().toString(), JobStatus.QUEUED.value(), hold.id().toString());
    }

    private SubmitJobView replay(IdempotencyRecord record, String hash) {
        if (!record.requestHash().equals(hash)) {
            throw JobException.idempotencyConflict();
        }
        Job job = jobs.findById(record.jobId()).orElseThrow(JobException::notFound);
        return new SubmitJobView(job.id().toString(), job.status().value(), job.holdId() == null ? null : job.holdId().toString());
    }

    private static String requireText(String raw, int maxLength) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw JobException.validation();
        }
        return value;
    }
}
