package art.pilipala.api.billing.infra;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.MembershipPlan;

@Mapper
public interface MembershipPlanMapper {

    String PLAN_COLS = "id, code, name_zh, name_en, sort_order, credits_per_month, concurrency, storage_gb, "
            + "audience, benefits_json::text AS benefits_json, enabled, created_at, updated_at";

    @Select("SELECT " + PLAN_COLS + " FROM membership_plan WHERE id = #{id}")
    MembershipPlan findById(@Param("id") long id);

    @Select("SELECT " + PLAN_COLS + " FROM membership_plan WHERE code = #{code}")
    MembershipPlan findByCode(@Param("code") String code);

    @Select("<script>SELECT " + PLAN_COLS + " FROM membership_plan WHERE 1=1"
            + "<if test='enabledOnly'> AND enabled = TRUE</if>"
            + "<if test='audience != null and audience != \"\"'> AND audience = #{audience}</if>"
            + " ORDER BY sort_order, id</script>")
    List<MembershipPlan> list(@Param("audience") String audience, @Param("enabledOnly") boolean enabledOnly);

    @Update("UPDATE membership_plan SET enabled = #{enabled}, updated_at = now() WHERE id = #{id}")
    int setEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Update("UPDATE membership_plan SET name_zh = #{nameZh}, name_en = #{nameEn}, sort_order = #{sortOrder}, "
            + "credits_per_month = #{creditsPerMonth}, storage_gb = #{storageGb}, benefits_json = "
            + "CAST(#{benefitsJson} AS jsonb), updated_at = now() WHERE id = #{id}")
    int update(@Param("id") long id,
            @Param("nameZh") String nameZh,
            @Param("nameEn") String nameEn,
            @Param("sortOrder") int sortOrder,
            @Param("creditsPerMonth") long creditsPerMonth,
            @Param("storageGb") int storageGb,
            @Param("benefitsJson") String benefitsJson);
}
