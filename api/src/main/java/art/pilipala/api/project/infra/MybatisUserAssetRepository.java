package art.pilipala.api.project.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.project.domain.UserAsset;

@Repository
public class MybatisUserAssetRepository implements UserAssetRepository {

    private final UserAssetMapper mapper;

    public MybatisUserAssetRepository(UserAssetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertGeneratedIfAbsent(UUID userId, UUID jobId, String objectKey, String mimeType) {
        mapper.insertGeneratedIfAbsent(userId, jobId, objectKey, mimeType);
    }

    @Override
    public List<UserAsset> findByUserAssets(UUID userId, int limit) {
        return mapper.findByUserAssets(userId, limit);
    }

    @Override
    public boolean deleteAsset(UUID assetId, UUID userId) {
        return mapper.deleteAsset(assetId, userId) > 0;
    }
}
