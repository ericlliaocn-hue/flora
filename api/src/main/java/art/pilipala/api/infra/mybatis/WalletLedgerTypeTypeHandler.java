package art.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.api.billing.domain.WalletLedgerType;

@MappedTypes(WalletLedgerType.class)
public class WalletLedgerTypeTypeHandler extends PilipalaEnumTypeHandler<WalletLedgerType> {

    public WalletLedgerTypeTypeHandler() {
        super(WalletLedgerType::of, WalletLedgerType::value);
    }
}
