package art.pilipala.api.admin.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import art.pilipala.api.admin.domain.ModelDisplayConfig;

@Repository
public class MybatisModelDisplayConfigRepository implements ModelDisplayConfigRepository {

    private final ModelDisplayConfigMapper mapper;

    public MybatisModelDisplayConfigRepository(ModelDisplayConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ModelDisplayConfig> findAll() {
        return mapper.findAll();
    }

    @Override
    public List<ModelDisplayConfig> findEnabled() {
        return mapper.findEnabled();
    }

    @Override
    public Optional<ModelDisplayConfig> findByModel(String model) {
        return Optional.ofNullable(mapper.findByModel(model));
    }

    @Override
    public ModelDisplayConfig upsert(String model, String capability, String displayName, boolean enabled, int sortOrder) {
        return mapper.upsert(model, capability, displayName, enabled, sortOrder);
    }
}
