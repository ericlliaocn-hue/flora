package com.flora.api.infra.mybatis;

import java.util.Locale;

import org.apache.ibatis.type.MappedTypes;

import com.flora.api.billing.domain.JobTerminalState;

@MappedTypes(JobTerminalState.class)
public class JobTerminalStateTypeHandler extends FloraEnumTypeHandler<JobTerminalState> {

    public JobTerminalStateTypeHandler() {
        super(
                JobTerminalState::of,
                state -> state.name().toLowerCase(Locale.ROOT));
    }
}
