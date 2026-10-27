package art.pilipala.api.billing.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import art.pilipala.api.billing.domain.MembershipPlan;
import art.pilipala.api.billing.domain.MembershipPlanPrice;
import art.pilipala.api.billing.infra.MembershipPlanMapper;
import art.pilipala.api.billing.infra.MembershipPlanPriceMapper;
import org.springframework.stereotype.Service;

@Service
public class ListMembershipPlans {

    private final MembershipPlanMapper plans;
    private final MembershipPlanPriceMapper prices;

    public ListMembershipPlans(MembershipPlanMapper plans, MembershipPlanPriceMapper prices) {
        this.plans = plans;
        this.prices = prices;
    }

    public MembershipPlansView listPublic(String audience) {
        String aud = audience == null || audience.isBlank() ? "personal" : audience;
        List<MembershipPlan> planRows = plans.list(aud, true);
        List<MembershipPlanPrice> priceRows = prices.list(null, true);
        java.util.Set<Long> enabledPlanIds = planRows.stream().map(MembershipPlan::id).collect(java.util.stream.Collectors.toSet());
        Map<Long, List<MembershipPlanPriceView>> pricesByPlan = new LinkedHashMap<>();
        for (MembershipPlanPrice p : priceRows) {
            if (!enabledPlanIds.contains(p.planId())) {
                continue;
            }
            pricesByPlan.computeIfAbsent(p.planId(), k -> new ArrayList<>()).add(MembershipPlanPriceView.of(p));
        }
        List<MembershipPlanView> out = new ArrayList<>();
        for (MembershipPlan plan : planRows) {
            List<MembershipPlanPriceView> planPrices = pricesByPlan.getOrDefault(plan.id(), List.of());
            planPrices.sort(Comparator.comparing(MembershipPlanPriceView::sort_order));
            out.add(MembershipPlanView.of(plan, planPrices));
        }
        return new MembershipPlansView(out);
    }

    public record MembershipPlansView(List<MembershipPlanView> plans) {
    }

    public record MembershipPlanView(
            long id,
            String code,
            String name_zh,
            String name_en,
            int sort_order,
            long credits_per_month,
            Integer concurrency,
            int storage_gb,
            String audience,
            String benefits_json,
            List<MembershipPlanPriceView> prices) {

        static MembershipPlanView of(MembershipPlan plan, List<MembershipPlanPriceView> prices) {
            return new MembershipPlanView(
                    plan.id(),
                    plan.code(),
                    plan.nameZh(),
                    plan.nameEn(),
                    plan.sortOrder(),
                    plan.creditsPerMonth(),
                    plan.concurrency(),
                    plan.storageGb(),
                    plan.audience(),
                    plan.benefitsJson(),
                    prices);
        }
    }

    public record MembershipPlanPriceView(
            long id,
            String cycle,
            String variant_code,
            long credits_per_month,
            int price_fen,
            Integer list_price_fen,
            Integer renew_price_fen,
            String badge_zh,
            int sort_order) {

        static MembershipPlanPriceView of(MembershipPlanPrice price) {
            return new MembershipPlanPriceView(
                    price.id(),
                    price.cycle(),
                    price.variantCode(),
                    price.creditsPerMonth(),
                    price.priceFen(),
                    price.listPriceFen(),
                    price.renewPriceFen(),
                    price.badgeZh(),
                    price.sortOrder());
        }
    }
}
