package com.flora.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.flora.api.billing.domain.WalletHoldStatus;

@MappedTypes(WalletHoldStatus.class)
public class WalletHoldStatusTypeHandler extends FloraEnumTypeHandler<WalletHoldStatus> {

    public WalletHoldStatusTypeHandler() {
        super(WalletHoldStatus::of, WalletHoldStatus::value);
    }
}
