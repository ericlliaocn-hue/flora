package com.flora.worker.infra.mybatis;

import java.util.Locale;

import org.apache.ibatis.type.MappedTypes;

import com.flora.worker.job.domain.WorkerJobStatus;

@MappedTypes(WorkerJobStatus.class)
public class WorkerJobStatusTypeHandler extends FloraEnumTypeHandler<WorkerJobStatus> {

    public WorkerJobStatusTypeHandler() {
        super(
                raw -> WorkerJobStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)),
                WorkerJobStatus::value);
    }
}
