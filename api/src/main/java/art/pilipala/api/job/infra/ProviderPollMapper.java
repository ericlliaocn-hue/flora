package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 视频 polling 的到期扫描。只碰 provider_task，不碰 job/钱包。
 *
 * <p>用 {@code UPDATE ... FOR UPDATE SKIP LOCKED ... RETURNING job_id} 把到期行先租出去，
 * 避免同一轮被多个 api 实例重复投递；租期到期后仍可重试。
 */
@Mapper
public interface ProviderPollMapper {

    @Select("UPDATE provider_task SET next_poll_at = now() + make_interval(secs => #{leaseSeconds}) "
            + "WHERE id IN ("
            + "  SELECT id FROM provider_task "
            + "  WHERE status IN ('submitted', 'polling') AND target_id IS NOT NULL AND next_poll_at <= now() "
            + "  ORDER BY next_poll_at LIMIT #{limit} FOR UPDATE SKIP LOCKED"
            + ") RETURNING job_id")
    List<UUID> claimDue(@Param("limit") int limit, @Param("leaseSeconds") int leaseSeconds);
}
