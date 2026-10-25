package com.pilipala.api.infra.mybatis;

import java.util.Locale;

import org.apache.ibatis.type.MappedTypes;

import com.pilipala.api.billing.domain.JobTerminalState;

@MappedTypes(JobTerminalState.class)
public class JobTerminalStateTypeHandler extends PilipalaEnumTypeHandler<JobTerminalState> {

    public JobTerminalStateTypeHandler() {
        super(
                JobTerminalState::of,
                state -> state.name().toLowerCase(Locale.ROOT));
    }
}
