package com.flora.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.flora.api.billing.domain.RechargeOrderStatus;

@MappedTypes(RechargeOrderStatus.class)
public class RechargeOrderStatusTypeHandler extends FloraEnumTypeHandler<RechargeOrderStatus> {

    public RechargeOrderStatusTypeHandler() {
        super(RechargeOrderStatus::of, RechargeOrderStatus::value);
    }
}
