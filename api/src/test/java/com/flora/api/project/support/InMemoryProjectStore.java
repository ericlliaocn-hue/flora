package com.flora.api.project.support;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.project.domain.AssetSource;
import com.flora.api.project.domain.CanvasProject;
import com.flora.api.project.domain.UserAsset;
import com.flora.api.project.infra.CanvasProjectRepository;
import com.flora.api.project.infra.UserAssetRepository;

/**
 * 内存版项目 / 素材仓储：云端持久化用例不需要 Postgres。
 *
 * <p>行为对齐 {@code Jdbc*}Repository：只查本人、版本号自增、
 * (job_id, object_key) 幂等不重。
 */
public class InMemoryProjectStore implements CanvasProjectRepository, UserAssetRepository {

    private final Map<UUID, CanvasProject> projects = new LinkedHashMap<>();
    private final Map<UUID, UserAsset> assets = new LinkedHashMap<>();

    @Override
    public CanvasProject insert(UUID userId, String name, String canvasDataJson) {
        Instant now = Instant.now();
        CanvasProject project = new CanvasProject(UUID.randomUUID(), userId, name, canvasDataJson, 1, now, now);
        projects.put(project.id(), project);
        return project;
    }

    @Override
    public Optional<CanvasProject> findByIdAndUser(UUID projectId, UUID userId) {
        CanvasProject project = projects.get(projectId);
        if (project == null || !project.userId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(project);
    }

    @Override
    public List<CanvasProject> findByUser(UUID userId, int limit) {
        return projects.values().stream()
                .filter(project -> project.userId().equals(userId))
                .sorted(Comparator.comparing(CanvasProject::updatedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<CanvasProject> update(UUID projectId, UUID userId, String name, String canvasDataJson) {
        CanvasProject project = projects.get(projectId);
        if (project == null || !project.userId().equals(userId)) {
            return Optional.empty();
        }
        CanvasProject updated = new CanvasProject(project.id(), project.userId(),
                name == null ? project.name() : name,
                canvasDataJson == null ? project.canvasDataJson() : canvasDataJson,
                project.version() + 1, project.createdAt(), Instant.now());
        projects.put(projectId, updated);
        return Optional.of(updated);
    }

    @Override
    public boolean delete(UUID projectId, UUID userId) {
        CanvasProject project = projects.get(projectId);
        if (project == null || !project.userId().equals(userId)) {
            return false;
        }
        projects.remove(projectId);
        return true;
    }

    @Override
    public void insertGeneratedIfAbsent(UUID userId, UUID jobId, String objectKey, String mimeType) {
        boolean exists = assets.values().stream()
                .anyMatch(asset -> asset.jobId() != null && asset.jobId().equals(jobId)
                        && asset.objectKey().equals(objectKey));
        if (exists) {
            return;
        }
        UserAsset asset = new UserAsset(UUID.randomUUID(), userId, null, jobId, AssetSource.GENERATED, objectKey, mimeType,
                Instant.now());
        assets.put(asset.id(), asset);
    }

    @Override
    public List<UserAsset> findByUserAssets(UUID userId, int limit) {
        return assets.values().stream()
                .filter(asset -> asset.userId().equals(userId))
                .sorted(Comparator.comparing(UserAsset::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public boolean deleteAsset(UUID assetId, UUID userId) {
        UserAsset asset = assets.get(assetId);
        if (asset == null || !asset.userId().equals(userId)) {
            return false;
        }
        assets.remove(assetId);
        return true;
    }

    /** 素材行数，测试用来断言「幂等同步没有产生重复行」。 */
    public int assetCount() {
        return assets.size();
    }
}
