package art.pilipala.worker.job.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkerOutboxMapper {

    @Insert("INSERT INTO outbox_message (id, aggregate_type, aggregate_id, payload, status) "
            + "VALUES (#{id}, #{aggregateType}, #{aggregateId}, #{payloadJson,jdbcType=OTHER}, 'pending')")
    void insertPending(
            @Param("id") UUID id,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId,
            @Param("payloadJson") String payloadJson);
}
