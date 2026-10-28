package art.pilipala.worker.job.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.worker.job.domain.WorkerJob;

@Mapper
public interface WorkerJobMapper {

    String COLS = "id, status, capability, model, product_id, target_id, params::text AS params_json, attempt, max_attempts, "
            + "cancel_requested_at, error_code, updated_at";

    @Select("SELECT " + COLS + " FROM job WHERE id = #{jobId}")
    WorkerJob findById(@Param("jobId") UUID jobId);

    @Select("UPDATE job SET status = 'running', attempt = attempt + 1, updated_at = now() "
            + "WHERE id = #{jobId} AND status = 'queued' RETURNING " + COLS)
    WorkerJob claim(@Param("jobId") UUID jobId);

    @Update("UPDATE job SET status = #{to}, error_code = #{errorCode}, updated_at = now() "
            + "WHERE id = #{jobId} AND status = #{from}")
    int updateStatus(
            @Param("jobId") UUID jobId,
            @Param("from") String from,
            @Param("to") String to,
            @Param("errorCode") String errorCode);

    @Update("UPDATE job SET result_object_keys = #{resultObjectKeysJson,jdbcType=VARCHAR}::jsonb, updated_at = now() "
            + "WHERE id = #{jobId} AND status = 'running'")
    int updateResult(@Param("jobId") UUID jobId, @Param("resultObjectKeysJson") String resultObjectKeysJson);
}
