package com.flora.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.flora.api.job.domain.JobStepStatus;

@MappedTypes(JobStepStatus.class)
public class JobStepStatusTypeHandler extends FloraEnumTypeHandler<JobStepStatus> {

    public JobStepStatusTypeHandler() {
        super(JobStepStatus::of, JobStepStatus::value);
    }
}
