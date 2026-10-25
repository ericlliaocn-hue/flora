package art.pilipala.api.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import art.pilipala.api.job.infra.JobException;
import org.junit.jupiter.api.Test;

/**
 * 状态机规则（纯函数，不碰数据库与队列）。
 */
class JobTransitionsTest {

    @Test
    void onlyCreatedCanBeQueued() {
        JobTransitions.requireQueued(JobStatus.CREATED);

        assertThatThrownBy(() -> JobTransitions.requireQueued(JobStatus.QUEUED))
                .isInstanceOf(JobException.class);
    }

    @Test
    void onlyQueuedCanStartRunning() {
        JobTransitions.requireRunning(JobStatus.QUEUED);

        assertThatThrownBy(() -> JobTransitions.requireRunning(JobStatus.RUNNING))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> JobTransitions.requireRunning(JobStatus.SUCCEEDED))
                .isInstanceOf(JobException.class);
    }

    @Test
    void onlyRunningCanFinish() {
        JobTransitions.requireSucceeded(JobStatus.RUNNING);
        JobTransitions.requireFailed(JobStatus.RUNNING);

        assertThatThrownBy(() -> JobTransitions.requireSucceeded(JobStatus.QUEUED))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> JobTransitions.requireFailed(JobStatus.SUCCEEDED))
                .isInstanceOf(JobException.class);
    }

    @Test
    void failedGoesBackToQueuedOrDies() {
        JobTransitions.requireRetry(JobStatus.FAILED);
        JobTransitions.requireDead(JobStatus.FAILED);

        assertThatThrownBy(() -> JobTransitions.requireRetry(JobStatus.RUNNING))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> JobTransitions.requireDead(JobStatus.DEAD))
                .isInstanceOf(JobException.class);
    }

    @Test
    void terminalStatesCannotBeCancelled() {
        JobTransitions.requireCancellable(JobStatus.CREATED);
        JobTransitions.requireCancellable(JobStatus.QUEUED);
        JobTransitions.requireCancellable(JobStatus.RUNNING);

        assertThatThrownBy(() -> JobTransitions.requireCancellable(JobStatus.SUCCEEDED))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> JobTransitions.requireCancellable(JobStatus.CANCELLED))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> JobTransitions.requireCancellable(JobStatus.DEAD))
                .isInstanceOf(JobException.class);
    }

    @Test
    void retryOnlyWhileAttemptsRemain() {
        assertThat(JobTransitions.canRetry(1, 3)).isTrue();
        assertThat(JobTransitions.canRetry(2, 3)).isTrue();
        assertThat(JobTransitions.canRetry(3, 3)).isFalse();
    }

    @Test
    void statusValuesAreLowercaseAndTerminalIsKnown() {
        assertThat(JobStatus.CREATED.value()).isEqualTo("created");
        assertThat(JobStatus.SUCCEEDED.value()).isEqualTo("succeeded");
        assertThat(JobStatus.DEAD.value()).isEqualTo("dead");

        assertThat(JobStatus.SUCCEEDED.isTerminal()).isTrue();
        assertThat(JobStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(JobStatus.DEAD.isTerminal()).isTrue();
        assertThat(JobStatus.FAILED.isTerminal()).isFalse();
        assertThat(JobStatus.QUEUED.isTerminal()).isFalse();
    }
}
