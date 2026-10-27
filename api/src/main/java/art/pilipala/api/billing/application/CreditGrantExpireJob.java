package art.pilipala.api.billing.application;

import java.time.Instant;
import java.util.List;

import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.infra.CreditGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditGrantExpireJob {

    private static final Logger log = LoggerFactory.getLogger(CreditGrantExpireJob.class);
    private static final int BATCH = 100;

    private final CreditGrantRepository grants;
    private final CreditGrantService grantService;

    public CreditGrantExpireJob(CreditGrantRepository grants, CreditGrantService grantService) {
        this.grants = grants;
        this.grantService = grantService;
    }

    @Transactional
    public int expireDueGrants() {
        List<CreditGrant> due = grants.findExpiredForUpdate(Instant.now(), BATCH);
        int count = 0;
        for (CreditGrant grant : due) {
            grantService.expireGrant(grant);
            count++;
        }
        if (count > 0) {
            log.info("credit grant expire tick count={}", count);
        }
        return count;
    }
}
