package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.job.domain.VideoFallbackJob;

/**
 * 产品级 fallback 的只读/原子写入 SQL。
 *
 * <p>只处理 status=failed 且 error_code 属于「上游任务失败 / 超时 / 网关不可用」的视频 job；
 * 参数/协议错误不 fallback。切换 target 不重算用户价、不二次冻结。
 */
@Mapper
public interface VideoFallbackMapper {

    @Select("SELECT id, product_id, target_id, error_code, attempt, max_attempts FROM job "
            + "WHERE status = 'failed' AND product_id IS NOT NULL AND target_id IS NOT NULL "
            + "AND cancel_requested_at IS NULL "
            + "AND error_code IN ('UPSTREAM_VIDEO_FAILED', 'GATEWAY_TIMEOUT', 'PROVIDER_UNAVAILABLE') "
            + "ORDER BY updated_at ASC LIMIT #{limit}")
    List<VideoFallbackJob> findFailed(@Param("limit") int limit);

    @Select("SELECT t.id::text FROM model_route r "
            + "JOIN model_target t ON t.id = r.target_id "
            + "JOIN model_route cur ON cur.product_id = r.product_id AND cur.target_id = #{currentTargetId} "
            + "WHERE r.product_id = #{productId} AND r.enabled AND t.enabled "
            + "AND r.priority > cur.priority "
            + "AND (r.max_cost_amount IS NULL OR t.cost_amount <= r.max_cost_amount) "
            + "ORDER BY r.priority ASC LIMIT 1")
    String findNextTarget(@Param("productId") UUID productId, @Param("currentTargetId") UUID currentTargetId);

    @Update("UPDATE job SET status = 'queued', target_id = #{nextTargetId}, error_code = NULL, "
            + "max_attempts = GREATEST(max_attempts, attempt + 1), updated_at = now() "
            + "WHERE id = #{jobId} AND status = 'failed' AND target_id = #{oldTargetId} "
            + "AND cancel_requested_at IS NULL")
    int requeue(
            @Param("jobId") UUID jobId,
            @Param("oldTargetId") UUID oldTargetId,
            @Param("nextTargetId") UUID nextTargetId);

    @Update("UPDATE job SET status = 'dead', error_code = 'NO_AVAILABLE_CHANNEL', updated_at = now() "
            + "WHERE id = #{jobId} AND status = 'failed' AND target_id = #{oldTargetId}")
    int markDead(@Param("jobId") UUID jobId, @Param("oldTargetId") UUID oldTargetId);
}
