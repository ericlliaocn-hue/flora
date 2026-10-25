package art.pilipala.api.auth.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.auth.domain.User;

@Mapper
public interface UserMapper {

    String COLS = "id, username, password_hash, locale, created_at, updated_at, role";

    @Select("SELECT " + COLS + " FROM users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT " + COLS + " FROM users WHERE id = #{id}")
    User findById(@Param("id") UUID id);

    @Select("SELECT count(*) FROM users WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    @Select("INSERT INTO users (username, password_hash, locale) VALUES (#{username}, #{passwordHash}, #{locale}) RETURNING " + COLS)
    User insert(@Param("username") String username, @Param("passwordHash") String passwordHash, @Param("locale") String locale);

    @Update("UPDATE users SET role = #{role}, updated_at = now() WHERE id = #{id}")
    int updateRole(@Param("id") UUID id, @Param("role") String role);
}
