package com.pilipala.worker.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import java.util.Locale;

import com.pilipala.worker.job.domain.WorkerStepStatus;

@MappedTypes(WorkerStepStatus.class)
public class WorkerStepStatusTypeHandler extends PilipalaEnumTypeHandler<WorkerStepStatus> {

    public WorkerStepStatusTypeHandler() {
        super(
                raw -> WorkerStepStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)),
                WorkerStepStatus::value);
    }
}
