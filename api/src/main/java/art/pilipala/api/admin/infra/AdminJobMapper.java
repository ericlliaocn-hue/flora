package art.pilipala.api.admin.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.admin.domain.AdminJob;

@Mapper
public interface AdminJobMapper {

    String COLS = "j.id AS job_id, j.user_id, u.username, j.status, j.capability, j.model, "
            + "COALESCE(j.params->>'prompt', '') AS prompt, j.attempt, j.max_attempts, j.hold_id, "
            + "h.status AS hold_status, j.error_code, j.created_at, j.updated_at";

    String FROM = " FROM job j JOIN users u ON u.id = j.user_id LEFT JOIN wallet_hold h ON h.id = j.hold_id ";

    @Select("<script>SELECT " + COLS + FROM + "WHERE 1 = 1"
            + "<if test='status != null and status != \"\"'> AND j.status = #{status}</if>"
            + "<if test='userId != null'> AND j.user_id = #{userId}</if>"
            + " ORDER BY j.created_at DESC LIMIT #{limit}</script>")
    List<AdminJob> findJobs(
            @Param("status") String status, @Param("userId") UUID userId, @Param("limit") int limit);

    @Select("SELECT " + COLS + FROM + "WHERE j.id = #{jobId}")
    AdminJob findJob(@Param("jobId") UUID jobId);
}
