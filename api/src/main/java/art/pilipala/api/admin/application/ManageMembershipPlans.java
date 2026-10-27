package art.pilipala.api.admin.application;

import java.util.List;

import art.pilipala.api.billing.domain.MembershipPlan;
import art.pilipala.api.billing.domain.MembershipPlanPrice;
import art.pilipala.api.billing.infra.MembershipPlanMapper;
import art.pilipala.api.billing.infra.MembershipPlanPriceMapper;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;

@Service
public class ManageMembershipPlans {

    private final MembershipPlanMapper plans;
    private final MembershipPlanPriceMapper prices;

    public ManageMembershipPlans(MembershipPlanMapper plans, MembershipPlanPriceMapper prices) {
        this.plans = plans;
        this.prices = prices;
    }

    public List<AdminMembershipPlanView> listPlans() {
        return plans.list(null, false).stream().map(AdminMembershipPlanView::of).toList();
    }

    public List<AdminMembershipPlanPriceView> listPrices(Long planId) {
        return prices.list(planId, false).stream().map(AdminMembershipPlanPriceView::of).toList();
    }

    public AdminMembershipPlanView setPlanEnabled(long id, boolean enabled) {
        MembershipPlan plan = plans.findById(id);
        if (plan == null) {
            throw JobException.validation();
        }
        plans.setEnabled(id, enabled);
        return AdminMembershipPlanView.of(plans.findById(id));
    }

    public AdminMembershipPlanPriceView setPriceEnabled(long id, boolean enabled) {
        MembershipPlanPrice price = prices.findById(id);
        if (price == null) {
            throw JobException.validation();
        }
        prices.setEnabled(id, enabled);
        return AdminMembershipPlanPriceView.of(prices.findById(id));
    }

    public record AdminMembershipPlanView(
            long id,
            String code,
            String name_zh,
            String name_en,
            int sort_order,
            long credits_per_month,
            boolean enabled) {

        static AdminMembershipPlanView of(MembershipPlan plan) {
            return new AdminMembershipPlanView(
                    plan.id(),
                    plan.code(),
                    plan.nameZh(),
                    plan.nameEn(),
                    plan.sortOrder(),
                    plan.creditsPerMonth(),
                    plan.enabled());
        }
    }

    public record AdminMembershipPlanPriceView(
            long id,
            long plan_id,
            String cycle,
            String variant_code,
            long credits_per_month,
            int price_fen,
            Integer list_price_fen,
            String badge_zh,
            boolean enabled) {

        static AdminMembershipPlanPriceView of(MembershipPlanPrice price) {
            return new AdminMembershipPlanPriceView(
                    price.id(),
                    price.planId(),
                    price.cycle(),
                    price.variantCode(),
                    price.creditsPerMonth(),
                    price.priceFen(),
                    price.listPriceFen(),
                    price.badgeZh(),
                    price.enabled());
        }
    }
}
