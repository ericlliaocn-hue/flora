package com.flora.api.job.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProviderTaskMapper {

    @Select("SELECT id, job_id, job_step_id, upstream_request_id, raw_status, raw_body::text AS raw_body, created_at "
            + "FROM provider_task WHERE job_id = #{jobId} ORDER BY created_at")
    List<ProviderTaskRow> findByJobId(@Param("jobId") UUID jobId);

    @Select("<script>SELECT id, job_id, job_step_id, upstream_request_id, raw_status, raw_body::text AS raw_body, created_at "
            + "FROM provider_task WHERE job_id IN "
            + "<foreach item='id' collection='jobIds' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY job_id, created_at</script>")
    List<ProviderTaskRow> findByJobIds(@Param("jobIds") List<UUID> jobIds);

    record ProviderTaskRow(
            UUID id,
            UUID jobId,
            UUID jobStepId,
            String upstreamRequestId,
            String rawStatus,
            String rawBody,
            Instant createdAt) {
    }
}
