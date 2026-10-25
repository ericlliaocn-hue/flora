package com.flora.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.flora.api.billing.domain.WalletLedgerType;

@MappedTypes(WalletLedgerType.class)
public class WalletLedgerTypeTypeHandler extends FloraEnumTypeHandler<WalletLedgerType> {

    public WalletLedgerTypeTypeHandler() {
        super(WalletLedgerType::of, WalletLedgerType::value);
    }
}
