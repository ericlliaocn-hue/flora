package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

/**
 * 视频 polling 到期扫描的仓储端口。实现只读/租用 provider_task，不做业务决策。
 */
public interface ProviderPollRepository {

    List<UUID> claimDue(int limit, int leaseSeconds);
}
