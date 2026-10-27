package art.pilipala.api.billing.infra;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.MembershipPlanPrice;

@Mapper
public interface MembershipPlanPriceMapper {

    String PRICE_COLS = "id, plan_id, cycle, variant_code, credits_per_month, price_fen, list_price_fen, "
            + "renew_price_fen, badge_zh, gift_months, sort_order, enabled, meta_json::text AS meta_json, "
            + "created_at, updated_at";

    @Select("SELECT " + PRICE_COLS + " FROM membership_plan_price WHERE id = #{id}")
    MembershipPlanPrice findById(@Param("id") long id);

    @Select("<script>SELECT " + PRICE_COLS + " FROM membership_plan_price WHERE 1=1"
            + "<if test='planId != null'> AND plan_id = #{planId}</if>"
            + "<if test='enabledOnly'> AND enabled = TRUE</if>"
            + " ORDER BY sort_order, id</script>")
    List<MembershipPlanPrice> list(@Param("planId") Long planId, @Param("enabledOnly") boolean enabledOnly);

    @Update("UPDATE membership_plan_price SET enabled = #{enabled}, updated_at = now() WHERE id = #{id}")
    int setEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Update("UPDATE membership_plan_price SET price_fen = #{priceFen}, list_price_fen = #{listPriceFen}, "
            + "badge_zh = #{badgeZh}, updated_at = now() WHERE id = #{id}")
    int updatePricing(@Param("id") long id,
            @Param("priceFen") int priceFen,
            @Param("listPriceFen") Integer listPriceFen,
            @Param("badgeZh") String badgeZh);
}
