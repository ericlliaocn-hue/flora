package com.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.pilipala.api.job.domain.JobStepStatus;

@MappedTypes(JobStepStatus.class)
public class JobStepStatusTypeHandler extends PilipalaEnumTypeHandler<JobStepStatus> {

    public JobStepStatusTypeHandler() {
        super(JobStepStatus::of, JobStepStatus::value);
    }
}
