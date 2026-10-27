package art.pilipala.api.billing.infra;

import art.pilipala.api.billing.application.SubscriptionPeriodGrantJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPeriodGrantDispatcher {

    private final SubscriptionPeriodGrantJob job;

    public SubscriptionPeriodGrantDispatcher(SubscriptionPeriodGrantJob job) {
        this.job = job;
    }

    @Scheduled(fixedDelayString = "${pilipala.billing.subscription-grant-poll-ms:900000}")
    public void tick() {
        job.processDueGrants();
    }
}
