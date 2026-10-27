package art.pilipala.api.billing.application;

import org.springframework.stereotype.Service;

@Service
public class SubscriptionPeriodGrantJob {

    private final MembershipActivationService activation;

    public SubscriptionPeriodGrantJob(MembershipActivationService activation) {
        this.activation = activation;
    }

    public int processDueGrants() {
        return activation.processDueGrants(50);
    }
}
