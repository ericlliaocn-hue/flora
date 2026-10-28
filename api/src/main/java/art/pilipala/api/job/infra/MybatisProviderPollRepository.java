package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class MybatisProviderPollRepository implements ProviderPollRepository {

    private final ProviderPollMapper mapper;

    public MybatisProviderPollRepository(ProviderPollMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<UUID> claimDue(int limit, int leaseSeconds) {
        return mapper.claimDue(limit, leaseSeconds).stream().map(UUID::fromString).toList();
    }
}
