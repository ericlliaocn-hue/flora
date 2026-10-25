package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.job.domain.OutboxMessage;

@Mapper
public interface OutboxMapper {

    String COLS = "id, aggregate_type, aggregate_id, payload AS payload_json, status, created_at, sent_at";

    @Select("INSERT INTO outbox_message (id, aggregate_type, aggregate_id, payload, status) "
            + "VALUES (#{id}, #{aggregateType}, #{aggregateId}, #{payloadJson,jdbcType=OTHER}, 'pending') RETURNING " + COLS)
    OutboxMessage insertPending(
            @Param("id") UUID id,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId,
            @Param("payloadJson") String payloadJson);

    @Select("SELECT " + COLS + " FROM outbox_message WHERE status = 'pending' ORDER BY created_at LIMIT #{limit}")
    List<OutboxMessage> findPending(@Param("limit") int limit);

    @Update("UPDATE outbox_message SET status = 'sent', sent_at = now() WHERE id = #{id} AND status = 'pending'")
    int markSent(@Param("id") UUID id);

    @Update("UPDATE outbox_message SET status = 'failed' WHERE id = #{id} AND status = 'pending'")
    int markFailed(@Param("id") UUID id);
}
