package art.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.api.billing.domain.RechargeOrderStatus;

@MappedTypes(RechargeOrderStatus.class)
public class RechargeOrderStatusTypeHandler extends PilipalaEnumTypeHandler<RechargeOrderStatus> {

    public RechargeOrderStatusTypeHandler() {
        super(RechargeOrderStatus::of, RechargeOrderStatus::value);
    }
}
