package art.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class MembershipCycle {

    public static final int GRANT_PERIOD_DAYS = 31;

    private MembershipCycle() {
    }

    public static Instant subscriptionEnd(Instant start, String cycle) {
        return switch (cycle) {
            case "month" -> start.plus(GRANT_PERIOD_DAYS, ChronoUnit.DAYS);
            case "quarter" -> start.plus(93, ChronoUnit.DAYS);
            case "year" -> start.plus(365, ChronoUnit.DAYS);
            default -> start.plus(GRANT_PERIOD_DAYS, ChronoUnit.DAYS);
        };
    }

    public static Instant grantExpires(Instant from) {
        return from.plus(GRANT_PERIOD_DAYS, ChronoUnit.DAYS);
    }
}
