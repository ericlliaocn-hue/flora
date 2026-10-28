package art.pilipala.worker.job.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkerJobStepMapper {

    @Select("INSERT INTO job_step (job_id, attempt, status, target_id) "
            + "VALUES (#{jobId}, #{attempt}, 'running', #{targetId,jdbcType=OTHER}) RETURNING id::text")
    String start(@Param("jobId") UUID jobId, @Param("attempt") int attempt, @Param("targetId") UUID targetId);

    @Update("UPDATE job_step SET status = #{status}, error_code = #{errorCode}, provider_task_id = #{providerTaskId}, "
            + "ended_at = now() WHERE job_id = #{jobId} AND attempt = #{attempt}")
    int end(
            @Param("jobId") UUID jobId,
            @Param("attempt") int attempt,
            @Param("status") String status,
            @Param("errorCode") String errorCode,
            @Param("providerTaskId") UUID providerTaskId);
}
