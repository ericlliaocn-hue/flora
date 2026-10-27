package art.pilipala.api.billing.infra;

import art.pilipala.api.billing.application.CreditGrantExpireJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CreditGrantExpireDispatcher {

    private final CreditGrantExpireJob job;

    public CreditGrantExpireDispatcher(CreditGrantExpireJob job) {
        this.job = job;
    }

    @Scheduled(fixedDelayString = "${pilipala.billing.grant-expire-poll-ms:600000}")
    public void tick() {
        job.expireDueGrants();
    }
}
