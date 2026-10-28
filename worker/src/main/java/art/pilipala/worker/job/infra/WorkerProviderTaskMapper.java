package art.pilipala.worker.job.infra;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.worker.job.domain.PollingTask;

@Mapper
public interface WorkerProviderTaskMapper {

    @Select("INSERT INTO provider_task (job_id, job_step_id, target_id, upstream_request_id, raw_status, raw_body) "
            + "VALUES (#{jobId}, #{jobStepId}, #{targetId,jdbcType=OTHER}, #{upstreamRequestId}, #{rawStatus}, "
            + "#{rawBodyJson,jdbcType=OTHER}) RETURNING id")
    UUID insert(
            @Param("jobId") UUID jobId,
            @Param("jobStepId") UUID jobStepId,
            @Param("targetId") UUID targetId,
            @Param("upstreamRequestId") String upstreamRequestId,
            @Param("rawStatus") String rawStatus,
            @Param("rawBodyJson") String rawBodyJson);

    @Select("INSERT INTO provider_task (job_id, job_step_id, target_id, upstream_task_id, status, next_poll_at, "
            + "raw_status, raw_body) "
            + "VALUES (#{jobId}, #{jobStepId}, #{targetId,jdbcType=OTHER}, #{upstreamTaskId}, 'polling', "
            + "#{nextPollAt}, #{rawStatus}, #{rawBodyJson,jdbcType=OTHER}) RETURNING id")
    UUID insertPolling(
            @Param("jobId") UUID jobId,
            @Param("jobStepId") UUID jobStepId,
            @Param("targetId") UUID targetId,
            @Param("upstreamTaskId") String upstreamTaskId,
            @Param("rawStatus") String rawStatus,
            @Param("rawBodyJson") String rawBodyJson,
            @Param("nextPollAt") Instant nextPollAt);

    @Select("SELECT pt.id AS provider_task_id, pt.job_id, pt.job_step_id, pt.target_id, pt.upstream_task_id, "
            + "pt.status, pt.poll_count, pt.raw_status, pt.raw_body::text AS raw_body_json "
            + "FROM provider_task pt "
            + "WHERE pt.job_id = #{jobId} AND pt.status IN ('submitted', 'polling') "
            + "ORDER BY pt.created_at DESC LIMIT 1")
    PollingTask findOpenByJob(@Param("jobId") UUID jobId);

    @Update("UPDATE provider_task SET status = 'polling', raw_status = #{rawStatus}, "
            + "raw_body = #{rawBodyJson,jdbcType=OTHER}, next_poll_at = #{nextPollAt}, "
            + "poll_count = #{pollCount}, last_poll_at = now() WHERE id = #{providerTaskId}")
    int reschedule(
            @Param("providerTaskId") UUID providerTaskId,
            @Param("rawStatus") String rawStatus,
            @Param("rawBodyJson") String rawBodyJson,
            @Param("nextPollAt") Instant nextPollAt,
            @Param("pollCount") int pollCount);

    @Update("UPDATE provider_task SET status = #{status}, raw_status = #{rawStatus}, "
            + "raw_body = #{rawBodyJson,jdbcType=OTHER}, error_class = #{errorClass}, "
            + "latency_ms = #{latencyMs}, last_poll_at = now() WHERE id = #{providerTaskId}")
    int finish(
            @Param("providerTaskId") UUID providerTaskId,
            @Param("status") String status,
            @Param("rawStatus") String rawStatus,
            @Param("rawBodyJson") String rawBodyJson,
            @Param("errorClass") String errorClass,
            @Param("latencyMs") Integer latencyMs);
}
