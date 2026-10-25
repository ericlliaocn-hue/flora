package com.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.pilipala.api.billing.domain.WalletHoldStatus;

@MappedTypes(WalletHoldStatus.class)
public class WalletHoldStatusTypeHandler extends PilipalaEnumTypeHandler<WalletHoldStatus> {

    public WalletHoldStatusTypeHandler() {
        super(WalletHoldStatus::of, WalletHoldStatus::value);
    }
}
