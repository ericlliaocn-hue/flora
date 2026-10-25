package com.flora.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.flora.api.job.domain.OutboxMessage;

@Repository
@Primary
public class MybatisOutboxRepository implements OutboxRepository {

    private final OutboxMapper mapper;

    public MybatisOutboxRepository(OutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OutboxMessage insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson) {
        return mapper.insertPending(id, aggregateType, aggregateId, payloadJson);
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return mapper.findPending(limit);
    }

    @Override
    public boolean markSent(UUID id) {
        return mapper.markSent(id) > 0;
    }

    @Override
    public boolean markFailed(UUID id) {
        return mapper.markFailed(id) > 0;
    }
}
