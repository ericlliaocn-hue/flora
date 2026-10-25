package art.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.api.billing.domain.WalletHoldStatus;

@MappedTypes(WalletHoldStatus.class)
public class WalletHoldStatusTypeHandler extends PilipalaEnumTypeHandler<WalletHoldStatus> {

    public WalletHoldStatusTypeHandler() {
        super(WalletHoldStatus::of, WalletHoldStatus::value);
    }
}
