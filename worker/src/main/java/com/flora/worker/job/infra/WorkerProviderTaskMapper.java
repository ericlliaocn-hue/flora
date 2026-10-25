package com.flora.worker.job.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkerProviderTaskMapper {

    @Select("INSERT INTO provider_task (job_id, job_step_id, upstream_request_id, raw_status, raw_body) "
            + "VALUES (#{jobId}, #{jobStepId}, #{upstreamRequestId}, #{rawStatus}, #{rawBodyJson,jdbcType=OTHER}) "
            + "RETURNING id")
    UUID insert(
            @Param("jobId") UUID jobId,
            @Param("jobStepId") UUID jobStepId,
            @Param("upstreamRequestId") String upstreamRequestId,
            @Param("rawStatus") String rawStatus,
            @Param("rawBodyJson") String rawBodyJson);
}
