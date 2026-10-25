package com.pilipala.api.project.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.pilipala.api.project.domain.UserAsset;

@Mapper
public interface UserAssetMapper {

    String COLS = "id, user_id, project_id, job_id, source, object_key, mime_type, created_at";

    @Insert("INSERT INTO user_asset (user_id, job_id, source, object_key, mime_type) "
            + "VALUES (#{userId}, #{jobId}, 'generated', #{objectKey}, #{mimeType}) "
            + "ON CONFLICT (job_id, object_key) DO NOTHING")
    void insertGeneratedIfAbsent(
            @Param("userId") UUID userId,
            @Param("jobId") UUID jobId,
            @Param("objectKey") String objectKey,
            @Param("mimeType") String mimeType);

    @Select("SELECT " + COLS + " FROM user_asset WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<UserAsset> findByUserAssets(@Param("userId") UUID userId, @Param("limit") int limit);

    @Delete("DELETE FROM user_asset WHERE id = #{assetId} AND user_id = #{userId}")
    int deleteAsset(@Param("assetId") UUID assetId, @Param("userId") UUID userId);
}
