package com.flora.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.flora.api.job.domain.JobStatus;

@MappedTypes(JobStatus.class)
public class JobStatusTypeHandler extends FloraEnumTypeHandler<JobStatus> {

    public JobStatusTypeHandler() {
        super(JobStatus::of, JobStatus::value);
    }
}
