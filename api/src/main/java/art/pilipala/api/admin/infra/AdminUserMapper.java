package art.pilipala.api.admin.infra;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.admin.domain.AdminUser;

@Mapper
public interface AdminUserMapper {

    String COLS = "u.id AS user_id, u.username, u.role, u.locale, u.created_at, u.updated_at, "
            + "COALESCE(w.available, 0) AS available, COALESCE(w.frozen, 0) AS frozen";

    @Select("SELECT " + COLS + " FROM users u LEFT JOIN wallet w ON w.user_id = u.id "
            + "ORDER BY u.created_at DESC LIMIT #{limit}")
    List<AdminUser> findUsersAll(@Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM users u LEFT JOIN wallet w ON w.user_id = u.id "
            + "WHERE u.username ILIKE #{pattern} ORDER BY u.created_at DESC LIMIT #{limit}")
    List<AdminUser> findUsersByQuery(@Param("pattern") String pattern, @Param("limit") int limit);
}
