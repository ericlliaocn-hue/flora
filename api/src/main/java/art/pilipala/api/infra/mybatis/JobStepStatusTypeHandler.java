package art.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.api.job.domain.JobStepStatus;

@MappedTypes(JobStepStatus.class)
public class JobStepStatusTypeHandler extends PilipalaEnumTypeHandler<JobStepStatus> {

    public JobStepStatusTypeHandler() {
        super(JobStepStatus::of, JobStepStatus::value);
    }
}
