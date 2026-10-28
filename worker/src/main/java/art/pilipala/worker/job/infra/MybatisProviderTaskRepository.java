package art.pilipala.worker.job.infra;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.worker.job.domain.PollingTask;

@Repository
public class MybatisProviderTaskRepository implements ProviderTaskRepository {

    private final WorkerProviderTaskMapper mapper;

    public MybatisProviderTaskRepository(WorkerProviderTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID insert(UUID jobId, UUID jobStepId, UUID targetId, String upstreamRequestId, String rawStatus,
            String rawBodyJson) {
        return mapper.insert(jobId, jobStepId, targetId, upstreamRequestId, rawStatus, rawBodyJson);
    }

    @Override
    public UUID insertPolling(UUID jobId, UUID jobStepId, UUID targetId, String upstreamTaskId,
            String rawStatus, String rawBodyJson, Instant nextPollAt) {
        return mapper.insertPolling(jobId, jobStepId, targetId, upstreamTaskId, rawStatus, rawBodyJson, nextPollAt);
    }

    @Override
    public Optional<PollingTask> findOpenByJob(UUID jobId) {
        return Optional.ofNullable(mapper.findOpenByJob(jobId));
    }

    @Override
    public boolean reschedule(UUID providerTaskId, String rawStatus, String rawBodyJson, Instant nextPollAt,
            int pollCount) {
        return mapper.reschedule(providerTaskId, rawStatus, rawBodyJson, nextPollAt, pollCount) > 0;
    }

    @Override
    public boolean finish(UUID providerTaskId, String status, String rawStatus, String rawBodyJson,
            String errorClass, Integer latencyMs) {
        return mapper.finish(providerTaskId, status, rawStatus, rawBodyJson, errorClass, latencyMs) > 0;
    }
}
