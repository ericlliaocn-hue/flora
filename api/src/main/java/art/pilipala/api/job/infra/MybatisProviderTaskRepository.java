package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.job.domain.ProviderTask;

@Repository
@Primary
public class MybatisProviderTaskRepository implements ProviderTaskRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProviderTaskMapper tasks;

    public MybatisProviderTaskRepository(ProviderTaskMapper tasks) {
        this.tasks = tasks;
    }

    @Override
    public List<ProviderTask> findByJobId(UUID jobId) {
        return tasks.findByJobId(jobId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ProviderTask> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        return tasks.findByJobIds(jobIds).stream().map(this::toDomain).toList();
    }

    private ProviderTask toDomain(ProviderTaskMapper.ProviderTaskRow row) {
        return new ProviderTask(
                row.id(),
                row.jobId(),
                row.jobStepId(),
                row.upstreamRequestId(),
                row.rawStatus(),
                readJson(row.rawBody()),
                row.createdAt());
    }

    private static JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            return MAPPER.getNodeFactory().textNode(raw);
        }
    }
}
