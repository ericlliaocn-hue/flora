package com.flora.worker.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import java.util.Locale;

import com.flora.worker.job.domain.WorkerStepStatus;

@MappedTypes(WorkerStepStatus.class)
public class WorkerStepStatusTypeHandler extends FloraEnumTypeHandler<WorkerStepStatus> {

    public WorkerStepStatusTypeHandler() {
        super(
                raw -> WorkerStepStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)),
                WorkerStepStatus::value);
    }
}
