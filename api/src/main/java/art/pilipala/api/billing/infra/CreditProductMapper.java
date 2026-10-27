package art.pilipala.api.billing.infra;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.CreditProduct;

@Mapper
public interface CreditProductMapper {

    String COLS = "id, code, tab, name_zh, name_en, description_zh, description_en, price_fen, list_price_fen, "
            + "credits, bonus_credits, first_purchase_bonus_credits, first_purchase_bonus_percent, validity_days, "
            + "min_plan_code, model_scope, sort_order, enabled, badge_zh, badge_en, meta_json, created_at, updated_at";

    @Select("SELECT " + COLS + " FROM credit_product WHERE code = #{code}")
    CreditProduct findByCode(@Param("code") String code);

    @Select("SELECT " + COLS + " FROM credit_product WHERE id = #{id}")
    CreditProduct findById(@Param("id") long id);

    @Select("<script>SELECT " + COLS + " FROM credit_product WHERE 1=1"
            + "<if test='tab != null and tab != \"\"'> AND tab = #{tab}</if>"
            + "<if test='enabledOnly'> AND enabled = TRUE</if>"
            + " ORDER BY sort_order, id</script>")
    List<CreditProduct> list(@Param("tab") String tab, @Param("enabledOnly") boolean enabledOnly);

    @Insert("INSERT INTO credit_product (code, tab, name_zh, name_en, description_zh, description_en, price_fen, "
            + "list_price_fen, credits, bonus_credits, first_purchase_bonus_credits, first_purchase_bonus_percent, "
            + "validity_days, min_plan_code, model_scope, sort_order, enabled, badge_zh, badge_en, meta_json) "
            + "VALUES (#{code}, #{tab}, #{nameZh}, #{nameEn}, #{descriptionZh}, #{descriptionEn}, #{priceFen}, "
            + "#{listPriceFen}, #{credits}, #{bonusCredits}, #{firstPurchaseBonusCredits}, "
            + "#{firstPurchaseBonusPercent}, #{validityDays}, #{minPlanCode}, #{modelScope}, #{sortOrder}, "
            + "#{enabled}, #{badgeZh}, #{badgeEn}, CAST(#{metaJson} AS jsonb)) RETURNING " + COLS)
    CreditProduct insert(@Param("code") String code,
            @Param("tab") String tab,
            @Param("nameZh") String nameZh,
            @Param("nameEn") String nameEn,
            @Param("descriptionZh") String descriptionZh,
            @Param("descriptionEn") String descriptionEn,
            @Param("priceFen") int priceFen,
            @Param("listPriceFen") Integer listPriceFen,
            @Param("credits") long credits,
            @Param("bonusCredits") long bonusCredits,
            @Param("firstPurchaseBonusCredits") long firstPurchaseBonusCredits,
            @Param("firstPurchaseBonusPercent") Integer firstPurchaseBonusPercent,
            @Param("validityDays") Integer validityDays,
            @Param("minPlanCode") String minPlanCode,
            @Param("modelScope") String modelScope,
            @Param("sortOrder") int sortOrder,
            @Param("enabled") boolean enabled,
            @Param("badgeZh") String badgeZh,
            @Param("badgeEn") String badgeEn,
            @Param("metaJson") String metaJson);

    @Update("UPDATE credit_product SET tab = #{tab}, name_zh = #{nameZh}, name_en = #{nameEn}, "
            + "description_zh = #{descriptionZh}, description_en = #{descriptionEn}, price_fen = #{priceFen}, "
            + "list_price_fen = #{listPriceFen}, credits = #{credits}, bonus_credits = #{bonusCredits}, "
            + "first_purchase_bonus_credits = #{firstPurchaseBonusCredits}, "
            + "first_purchase_bonus_percent = #{firstPurchaseBonusPercent}, validity_days = #{validityDays}, "
            + "min_plan_code = #{minPlanCode}, model_scope = #{modelScope}, sort_order = #{sortOrder}, "
            + "badge_zh = #{badgeZh}, badge_en = #{badgeEn}, meta_json = CAST(#{metaJson} AS jsonb), "
            + "updated_at = now() WHERE id = #{id} RETURNING " + COLS)
    CreditProduct update(@Param("id") long id,
            @Param("tab") String tab,
            @Param("nameZh") String nameZh,
            @Param("nameEn") String nameEn,
            @Param("descriptionZh") String descriptionZh,
            @Param("descriptionEn") String descriptionEn,
            @Param("priceFen") int priceFen,
            @Param("listPriceFen") Integer listPriceFen,
            @Param("credits") long credits,
            @Param("bonusCredits") long bonusCredits,
            @Param("firstPurchaseBonusCredits") long firstPurchaseBonusCredits,
            @Param("firstPurchaseBonusPercent") Integer firstPurchaseBonusPercent,
            @Param("validityDays") Integer validityDays,
            @Param("minPlanCode") String minPlanCode,
            @Param("modelScope") String modelScope,
            @Param("sortOrder") int sortOrder,
            @Param("badgeZh") String badgeZh,
            @Param("badgeEn") String badgeEn,
            @Param("metaJson") String metaJson);

    @Update("UPDATE credit_product SET enabled = #{enabled}, updated_at = now() WHERE id = #{id}")
    int setEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Select("SELECT COUNT(*) FROM credit_product")
    long countAll();
}
