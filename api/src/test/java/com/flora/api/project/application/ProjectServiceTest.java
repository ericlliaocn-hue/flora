package com.flora.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flora.api.common.ErrorCodes;
import com.flora.api.project.infra.ProjectException;
import com.flora.api.project.support.InMemoryProjectStore;
import org.junit.jupiter.api.Test;

/**
 * 云端项目用例：创建、列表、详情、更新、删除，以及跨用户隔离与参数校验。
 */
class ProjectServiceTest {

    private final InMemoryProjectStore store = new InMemoryProjectStore();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectService service = new ProjectService(store, mapper);

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void createStoresTheSnapshotAndReturnsVersionOne() {
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.putArray("nodes");

        ProjectDetailView created = service.create(owner, "我的画布", snapshot);

        assertThat(created.name()).isEqualTo("我的画布");
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.canvasData().isObject()).isTrue();
        assertThat(UUID.fromString(created.id())).isNotNull();
    }

    @Test
    void createWithoutCanvasDataStoresAnEmptyObject() {
        ProjectDetailView created = service.create(owner, "空画布", null);

        assertThat(created.canvasData().isObject()).isTrue();
        assertThat(created.canvasData().isEmpty()).isTrue();
    }

    @Test
    void listReturnsOnlyTheOwnProjects() {
        service.create(owner, "A", null);
        service.create(owner, "B", null);
        service.create(other, "别人的", null);

        assertThat(service.list(owner)).hasSize(2);
        assertThat(service.list(owner).stream().map(ProjectSummaryView::name)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void getOfSomebodyElseProjectIsNotFound() {
        ProjectDetailView created = service.create(owner, "A", null);

        assertThatThrownBy(() -> service.get(other, UUID.fromString(created.id())))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.PROJECT_NOT_FOUND);
    }

    @Test
    void updateBumpsTheVersionAndKeepsTheUntouchedFields() {
        ProjectDetailView created = service.create(owner, "A", null);
        UUID projectId = UUID.fromString(created.id());

        ProjectDetailView updated = service.update(owner, projectId, "改名", null);

        assertThat(updated.name()).isEqualTo("改名");
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void updateOfSomebodyElseProjectIsNotFound() {
        ProjectDetailView created = service.create(owner, "A", null);

        assertThatThrownBy(() -> service.update(other, UUID.fromString(created.id()), "抢", null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.PROJECT_NOT_FOUND);
    }

    @Test
    void deleteOfSomebodyElseProjectIsNotFound() {
        ProjectDetailView created = service.create(owner, "A", null);

        assertThatThrownBy(() -> service.delete(other, UUID.fromString(created.id())))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.PROJECT_NOT_FOUND);
        assertThat(service.list(owner)).hasSize(1);
    }

    @Test
    void deleteRemovesTheProject() {
        ProjectDetailView created = service.create(owner, "A", null);

        DeleteProjectView deleted = service.delete(owner, UUID.fromString(created.id()));

        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.id()).isEqualTo(created.id());
        assertThat(service.list(owner)).isEmpty();
    }

    @Test
    void blankOrTooLongNameIsRejected() {
        assertThatThrownBy(() -> service.create(owner, "   ", null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.create(owner, "x".repeat(129), null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }

    @Test
    void nonObjectCanvasDataIsRejected() {
        assertThatThrownBy(() -> service.create(owner, "A", mapper.createArrayNode()))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }

    @Test
    void updateWithoutAnyFieldIsRejected() {
        ProjectDetailView created = service.create(owner, "A", null);

        assertThatThrownBy(() -> service.update(owner, UUID.fromString(created.id()), null, null))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }
}
