package art.pilipala.worker.job.infra;

import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.worker.job.domain.WorkerTarget;

@Mapper
public interface WorkerTargetMapper {

    @Select("SELECT id, code, gateway, upstream_model, timeout_seconds, enabled "
            + "FROM model_target WHERE id = #{targetId}")
    WorkerTarget findById(@Param("targetId") UUID targetId);
}
