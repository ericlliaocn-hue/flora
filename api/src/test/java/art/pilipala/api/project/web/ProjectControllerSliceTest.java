package art.pilipala.api.project.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.SaTokenConfig;
import art.pilipala.api.project.application.AssetService;
import art.pilipala.api.project.application.DeleteProjectView;
import art.pilipala.api.project.application.ProjectDetailView;
import art.pilipala.api.project.application.ProjectService;
import art.pilipala.api.project.application.ProjectSummaryView;
import art.pilipala.api.project.application.UserAssetView;
import art.pilipala.api.project.infra.ProjectException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C 端项目 / 素材接口的切片测试：信封、snake_case 字段、跨用户隔离。
 *
 * <p>鉴权闸门（SaTokenConfig）在这个切片里被排除掉：会话由 mock 的
 * {@link AuthSessionService} 提供，只测控制器与信封这一层。
 */
@WebMvcTest(controllers = { ProjectController.class, AssetController.class },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
class ProjectControllerSliceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionService sessions;

    @MockitoBean
    private ProjectService projects;

    @MockitoBean
    private AssetService assets;

    @Test
    void createReturnsTheProjectDetail() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        ObjectNode canvasData = new ObjectMapper().createObjectNode();
        canvasData.putArray("nodes");
        when(projects.create(eq(USER), eq("我的画布"), any())).thenReturn(new ProjectDetailView(
                PROJECT_ID.toString(), "我的画布", canvasData, 1,
                java.time.Instant.parse("2026-10-24T00:00:00Z"), java.time.Instant.parse("2026-10-24T00:00:00Z")));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"我的画布","canvas_data":{"nodes":[]}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data.name").value("我的画布"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.canvas_data.nodes").isArray());
    }

    @Test
    void listReturnsSummariesWithoutCanvasData() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(projects.list(USER)).thenReturn(List.of(new ProjectSummaryView(PROJECT_ID.toString(), "画布 1", 2,
                java.time.Instant.parse("2026-10-24T00:00:00Z"), java.time.Instant.parse("2026-10-24T00:05:00Z"))));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].canvas_data").doesNotExist());
    }

    @Test
    void getOfSomebodyElseProjectIsNotFound() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(projects.get(eq(USER), eq(PROJECT_ID))).thenThrow(ProjectException.projectNotFound());

        mockMvc.perform(get("/api/v1/projects/" + PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("项目不存在。"));
    }

    @Test
    void malformedProjectIdIsNotFound() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());

        mockMvc.perform(get("/api/v1/projects/not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void updateBumpsTheVersion() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(projects.update(eq(USER), eq(PROJECT_ID), eq("改名"), any())).thenReturn(new ProjectDetailView(
                PROJECT_ID.toString(), "改名", new ObjectMapper().createObjectNode(), 2,
                java.time.Instant.parse("2026-10-24T00:00:00Z"), java.time.Instant.parse("2026-10-24T00:05:00Z")));

        mockMvc.perform(put("/api/v1/projects/" + PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"改名"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void deleteReturnsTheDeletedFlag() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(projects.delete(USER, PROJECT_ID)).thenReturn(new DeleteProjectView(PROJECT_ID.toString(), true));

        mockMvc.perform(delete("/api/v1/projects/" + PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void assetsAreListedWithAFreshSignedUrl() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(assets.listUserAssets(eq(USER), eq(100))).thenReturn(List.of(new UserAssetView(
                ASSET_ID.toString(), null, PROJECT_ID.toString(), "generated", "anytoken/img/a/1.png",
                "http://127.0.0.1:18080/flora-dev/anytoken/img/a/1.png?X-Tos-Signature=abc", "image/png",
                java.time.Instant.parse("2026-10-24T00:10:00Z"))));

        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].object_key").value("anytoken/img/a/1.png"))
                .andExpect(jsonPath("$.data[0].signed_url").value(
                        "http://127.0.0.1:18080/flora-dev/anytoken/img/a/1.png?X-Tos-Signature=abc"))
                .andExpect(jsonPath("$.data[0].source").value("generated"));
    }

    @Test
    void deletingSomebodyElseAssetIsNotFound() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(assets.deleteAsset(eq(USER), eq(ASSET_ID))).thenThrow(ProjectException.assetNotFound());

        mockMvc.perform(delete("/api/v1/assets/" + ASSET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("ASSET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("素材不存在。"));
    }

    /** ADR 0005：异常原文一律不外泄。 */
    @Test
    void anUncaughtRuntimeExceptionIsSanitized() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(projects.list(USER)).thenThrow(new IllegalStateException("relation \"canvas_project\" does not exist"));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("canvas_project"))));
    }
}
