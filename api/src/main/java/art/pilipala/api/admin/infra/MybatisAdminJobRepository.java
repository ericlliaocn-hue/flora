package art.pilipala.api.admin.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.admin.domain.AdminJob;

@Repository
public class MybatisAdminJobRepository implements AdminJobRepository {

    private final AdminJobMapper mapper;

    public MybatisAdminJobRepository(AdminJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AdminJob> findJobs(String status, UUID userId, int limit) {
        String normalized = status == null || status.isBlank() ? null : status.trim();
        return mapper.findJobs(normalized, userId, limit);
    }

    @Override
    public Optional<AdminJob> findJob(UUID jobId) {
        return Optional.ofNullable(mapper.findJob(jobId));
    }
}
