package art.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.api.job.domain.JobStatus;

@MappedTypes(JobStatus.class)
public class JobStatusTypeHandler extends PilipalaEnumTypeHandler<JobStatus> {

    public JobStatusTypeHandler() {
        super(JobStatus::of, JobStatus::value);
    }
}
