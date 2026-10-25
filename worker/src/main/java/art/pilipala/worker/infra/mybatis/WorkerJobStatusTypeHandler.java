package art.pilipala.worker.infra.mybatis;

import java.util.Locale;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.worker.job.domain.WorkerJobStatus;

@MappedTypes(WorkerJobStatus.class)
public class WorkerJobStatusTypeHandler extends PilipalaEnumTypeHandler<WorkerJobStatus> {

    public WorkerJobStatusTypeHandler() {
        super(
                raw -> WorkerJobStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)),
                WorkerJobStatus::value);
    }
}
