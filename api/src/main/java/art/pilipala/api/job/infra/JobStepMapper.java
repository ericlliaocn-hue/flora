package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.job.domain.JobStep;

@Mapper
public interface JobStepMapper {

    String COLS = "id, job_id, attempt, status, error_code, provider_task_id, started_at, ended_at";

    @Select("INSERT INTO job_step (job_id, attempt, status) VALUES (#{jobId}, #{attempt}, 'running') RETURNING " + COLS)
    JobStep start(@Param("jobId") UUID jobId, @Param("attempt") int attempt);

    @Update("UPDATE job_step SET status = #{status}, error_code = #{errorCode}, ended_at = now() "
            + "WHERE job_id = #{jobId} AND attempt = #{attempt}")
    int end(
            @Param("jobId") UUID jobId,
            @Param("attempt") int attempt,
            @Param("status") String status,
            @Param("errorCode") String errorCode);

    @Select("<script>SELECT " + COLS + " FROM job_step WHERE job_id IN "
            + "<foreach item='id' collection='jobIds' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY job_id, attempt</script>")
    List<JobStep> findByJobIds(@Param("jobIds") List<UUID> jobIds);
}
