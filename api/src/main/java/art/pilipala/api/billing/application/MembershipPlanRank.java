package art.pilipala.api.billing.application;

import java.util.Map;

/** 会员档门槛比较：code → 等级，越大越高。 */
public final class MembershipPlanRank {

    private static final Map<String, Integer> RANK = Map.of(
            "standard", 1,
            "plus", 2,
            "pro", 3,
            "deluxe", 4,
            "ultra", 5);

    private MembershipPlanRank() {
    }

    public static int rankOf(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return 0;
        }
        return RANK.getOrDefault(planCode.trim().toLowerCase(), 0);
    }

    public static boolean meetsMinPlan(String userPlanCode, String minPlanCode) {
        if (minPlanCode == null || minPlanCode.isBlank()) {
            return true;
        }
        return rankOf(userPlanCode) >= rankOf(minPlanCode);
    }
}
