package art.pilipala.api.job.infra;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import art.pilipala.api.job.domain.OutboxMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 假 Transport：把消息记在内存里，供测试断言与本地无 broker 演练。
 *
 * <p>只在 {@code pilipala.job.transport=fake} 时装配；生产默认走
 * {@link RabbitJobTransport}。
 */
@Component
@ConditionalOnProperty(name = "pilipala.job.transport", havingValue = "fake")
public class FakeJobTransport implements JobMessageTransport {

    private final List<OutboxMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void publish(OutboxMessage message) {
        sent.add(message);
    }

    /** 已经投递出去的消息。 */
    public List<OutboxMessage> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
