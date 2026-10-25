package com.flora.api.job.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.flora.api.job.domain.IdempotencyRecord;

@Repository
public class MybatisIdempotencyRepository implements IdempotencyRepository {

    private final IdempotencyMapper mapper;

    public MybatisIdempotencyRepository(IdempotencyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<IdempotencyRecord> findByUserAndKey(UUID userId, String key) {
        return Optional.ofNullable(mapper.findByUserAndKey(userId, key));
    }

    @Override
    public void insert(UUID userId, String key, UUID jobId, String requestHash) {
        mapper.insert(userId, key, jobId, requestHash);
    }
}
