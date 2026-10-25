package art.pilipala.worker.job.infra;

import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class MybatisWorkerOutboxRepository implements WorkerOutboxRepository {

    private final WorkerOutboxMapper mapper;

    public MybatisWorkerOutboxRepository(WorkerOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson) {
        mapper.insertPending(id, aggregateType, aggregateId, payloadJson);
    }
}
