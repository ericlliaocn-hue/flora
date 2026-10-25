package com.pilipala.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilipala.api.common.ErrorCodes;
import com.pilipala.api.job.domain.JobStatus;
import com.pilipala.api.job.support.InMemoryJobStore;
import com.pilipala.api.project.infra.ProjectException;
import com.pilipala.api.project.support.InMemoryProjectStore;
import com.pilipala.api.storage.TosProperties;
import com.pilipala.api.storage.TosUrlSigner;
import org.junit.jupiter.api.Test;

/**
 * 云端素材用例：自动同步生成结果、现签读地址、删除与跨用户隔离。
 */
class AssetServiceTest {

    private final InMemoryProjectStore store = new InMemoryProjectStore();
    private final InMemoryJobStore jobs = new InMemoryJobStore();
    private final ObjectMapper mapper = new ObjectMapper();
    private final TosUrlSigner signer = new TosUrlSigner(new TosProperties("stub", "http://127.0.0.1:18080", "cn-beijing",
            "flora-dev", "flora-local-ak", "flora-local-sk", 900, 15000, "/tmp/flora-tos"));
    private final AssetService service = new AssetService(store, jobs, signer, mapper);

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void succeededJobsAreSyncedIntoTheAssetTable() {
        UUID jobId = succeededJob(owner, "anytoken/img/" + owner + "/1.png");

        List<UserAssetView> assets = service.listUserAssets(owner, 100);

        assertThat(assets).hasSize(1);
        assertThat(assets.get(0).jobId()).isEqualTo(jobId.toString());
        assertThat(assets.get(0).source()).isEqualTo("generated");
        assertThat(assets.get(0).objectKey()).isEqualTo("anytoken/img/" + owner + "/1.png");
        assertThat(assets.get(0).signedUrl()).startsWith("http://127.0.0.1:18080/flora-dev/anytoken/img/");
    }

    @Test
    void syncingTwiceDoesNotDuplicateRows() {
        succeededJob(owner, "anytoken/img/x/1.png");

        service.listUserAssets(owner, 100);
        service.listUserAssets(owner, 100);

        assertThat(store.assetCount()).isEqualTo(1);
    }

    @Test
    void anotherUsersJobsAreNotSynced() {
        succeededJob(other, "anytoken/img/other/1.png");

        assertThat(service.listUserAssets(owner, 100)).isEmpty();
    }

    @Test
    void failedJobsAreNotSynced() {
        UUID jobId = jobs.insertIfAbsent(owner, "image.generate", "demo", "{}", "k1", null).orElseThrow().id();
        jobs.updateStatus(jobId, JobStatus.CREATED, JobStatus.DEAD, "JOB_FAILED");

        assertThat(service.listUserAssets(owner, 100)).isEmpty();
    }

    @Test
    void deleteOfSomebodyElseAssetIsNotFound() {
        succeededJob(owner, "anytoken/img/x/1.png");
        UserAssetView asset = service.listUserAssets(owner, 100).get(0);

        assertThatThrownBy(() -> service.deleteAsset(other, UUID.fromString(asset.id())))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.ASSET_NOT_FOUND);
    }

    @Test
    void deleteRemovesTheAssetRow() {
        succeededJob(owner, "anytoken/img/x/1.png");
        UserAssetView asset = service.listUserAssets(owner, 100).get(0);

        DeleteProjectView deleted = service.deleteAsset(owner, UUID.fromString(asset.id()));

        assertThat(deleted.deleted()).isTrue();
        assertThat(store.assetCount()).isEqualTo(0);
    }

    /**
     * WP10 的自动同步语义：素材行由「已成功的任务」驱动，
     * 所以删掉一条 generated 素材后，下一次列表会按任务历史重新同步回来。
     */
    @Test
    void aDeletedGeneratedAssetIsSyncedBackFromTheSucceededJob() {
        succeededJob(owner, "anytoken/img/x/1.png");
        UserAssetView asset = service.listUserAssets(owner, 100).get(0);
        service.deleteAsset(owner, UUID.fromString(asset.id()));

        assertThat(service.listUserAssets(owner, 100)).hasSize(1);
    }

    /** 建一条成功且带结果对象键的 job。 */
    private UUID succeededJob(UUID userId, String objectKey) {
        UUID jobId = jobs.insertIfAbsent(userId, "image.generate", "demo", "{\"prompt\":\"hi\"}", "k-" + UUID.randomUUID(), null)
                .orElseThrow()
                .id();
        jobs.updateStatus(jobId, JobStatus.CREATED, JobStatus.RUNNING, null);
        jobs.updateResult(jobId, "[\"" + objectKey + "\"]");
        jobs.updateStatus(jobId, JobStatus.RUNNING, JobStatus.SUCCEEDED, null);
        return jobId;
    }
}
