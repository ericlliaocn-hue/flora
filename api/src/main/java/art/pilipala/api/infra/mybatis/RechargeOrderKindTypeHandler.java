package art.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.api.billing.domain.RechargeOrderKind;

@MappedTypes(RechargeOrderKind.class)
public class RechargeOrderKindTypeHandler extends PilipalaEnumTypeHandler<RechargeOrderKind> {

    public RechargeOrderKindTypeHandler() {
        super(RechargeOrderKind::of, RechargeOrderKind::value);
    }
}
