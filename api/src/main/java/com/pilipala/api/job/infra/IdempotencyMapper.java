package com.pilipala.api.job.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.pilipala.api.job.domain.IdempotencyRecord;

@Mapper
public interface IdempotencyMapper {

    String COLS = "id, user_id, \"key\", job_id, request_hash, created_at";

    @Select("SELECT " + COLS + " FROM idempotency_record WHERE user_id = #{userId} AND \"key\" = #{key}")
    IdempotencyRecord findByUserAndKey(@Param("userId") UUID userId, @Param("key") String key);

    @Insert("INSERT INTO idempotency_record (user_id, \"key\", job_id, request_hash) VALUES (#{userId}, #{key}, #{jobId}, #{requestHash})")
    void insert(
            @Param("userId") UUID userId,
            @Param("key") String key,
            @Param("jobId") UUID jobId,
            @Param("requestHash") String requestHash);
}
