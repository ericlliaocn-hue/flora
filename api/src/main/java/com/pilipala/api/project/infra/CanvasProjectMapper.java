package com.pilipala.api.project.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.pilipala.api.project.domain.CanvasProject;

@Mapper
public interface CanvasProjectMapper {

    String COLS = "id, user_id, name, canvas_data AS canvas_data_json, version, created_at, updated_at";

    @Select("INSERT INTO canvas_project (user_id, name, canvas_data) VALUES "
            + "(#{userId}, #{name}, #{canvasDataJson,jdbcType=OTHER}) RETURNING " + COLS)
    CanvasProject insert(
            @Param("userId") UUID userId, @Param("name") String name, @Param("canvasDataJson") String canvasDataJson);

    @Select("SELECT " + COLS + " FROM canvas_project WHERE id = #{projectId} AND user_id = #{userId}")
    CanvasProject findByIdAndUser(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Select("SELECT " + COLS + " FROM canvas_project WHERE user_id = #{userId} ORDER BY updated_at DESC LIMIT #{limit}")
    List<CanvasProject> findByUser(@Param("userId") UUID userId, @Param("limit") int limit);

    @Select("UPDATE canvas_project SET name = COALESCE(#{name}, name), "
            + "canvas_data = COALESCE(#{canvasDataJson,jdbcType=OTHER}, canvas_data), "
            + "version = version + 1, updated_at = now() WHERE id = #{projectId} AND user_id = #{userId} RETURNING " + COLS)
    CanvasProject update(
            @Param("projectId") UUID projectId,
            @Param("userId") UUID userId,
            @Param("name") String name,
            @Param("canvasDataJson") String canvasDataJson);

    @Delete("DELETE FROM canvas_project WHERE id = #{projectId} AND user_id = #{userId}")
    int delete(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
