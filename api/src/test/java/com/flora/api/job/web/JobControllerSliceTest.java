package com.flora.api.job.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.flora.api.auth.infra.AuthSessionService;
import com.flora.api.auth.infra.SaTokenConfig;
import com.flora.api.job.application.CancelJob;
import com.flora.api.job.application.GetJob;
import com.flora.api.job.application.GetJobResult;
import com.flora.api.job.application.JobOutput;
import com.flora.api.job.application.JobResultView;
import com.flora.api.job.application.JobView;
import com.flora.api.job.application.SubmitJob;
import com.flora.api.job.application.SubmitJobView;
import com.flora.api.job.infra.JobException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C 端任务接口的切片测试：信封、snake_case 字段、失败时的 error_code。
 *
 * <p>鉴权闸门（SaTokenConfig）在这个切片里被排除掉：会话由 mock 的
 * {@link AuthSessionService} 提供，只测控制器与信封这一层。
 */
@WebMvcTest(controllers = JobController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
class JobControllerSliceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionService sessions;

    @MockitoBean
    private SubmitJob submitJob;

    @MockitoBean
    private GetJob getJob;

    @MockitoBean
    private GetJobResult getJobResult;

    @MockitoBean
    private CancelJob cancelJob;

    @Test
    void submitReturnsTheQueuedJob() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(submitJob.submit(eq(USER), eq("k1"), eq("image.generate"), eq("demo"), any(JsonNode.class), isNull()))
                .thenReturn(new SubmitJobView(JOB_ID.toString(), "queued", null));

        mockMvc.perform(post("/api/v1/jobs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"hi"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.error_code").doesNotExist())
                .andExpect(jsonPath("$.data.job_id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.hold_id").doesNotExist());
    }

    @Test
    void submitFailureUsesTheEnvelopeWithAStableCode() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(submitJob.submit(eq(USER), eq("k1"), any(), any(), any(JsonNode.class), any()))
                .thenThrow(JobException.idempotencyConflict());

        mockMvc.perform(post("/api/v1/jobs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error_code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("同一个幂等键对应了不同的请求内容。"));
    }

    @Test
    void queryOfSomebodyElseJobIsNotFound() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(getJob.get(eq(USER), eq(JOB_ID), any())).thenThrow(JobException.notFound());

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("任务不存在。"));
    }

    @Test
    void queryReturnsTheJobState() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(getJob.get(eq(USER), eq(JOB_ID), any()))
                .thenReturn(new JobView(JOB_ID.toString(), "queued", 1, 3, null, null, "2026-10-24T00:00:00Z"));

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.attempt").value(1))
                .andExpect(jsonPath("$.data.max_attempts").value(3))
                .andExpect(jsonPath("$.data.updated_at").value("2026-10-24T00:00:00Z"));
    }

    @Test
    void resultExposesObjectKeysWithoutUrls() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(getJobResult.result(eq(USER), eq(JOB_ID), any()))
                .thenReturn(new JobResultView(JOB_ID.toString(), "succeeded",
                        List.of(new JobOutput("image", "img/a.png", null)), null, null));

        mockMvc.perform(get("/api/v1/jobs/" + JOB_ID + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("succeeded"))
                .andExpect(jsonPath("$.data.outputs[0].type").value("image"))
                .andExpect(jsonPath("$.data.outputs[0].object_key").value("img/a.png"))
                .andExpect(jsonPath("$.data.outputs[0].url").doesNotExist());
    }

    @Test
    void cancelReturnsTheCurrentState() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(cancelJob.cancel(eq(USER), eq(JOB_ID), any()))
                .thenReturn(new JobView(JOB_ID.toString(), "cancelled", 0, 3, "JOB_CANCELLED", "任务已取消。",
                        "2026-10-24T00:00:00Z"));

        mockMvc.perform(post("/api/v1/jobs/" + JOB_ID + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("cancelled"))
                .andExpect(jsonPath("$.data.error_code").value("JOB_CANCELLED"));
    }

    @Test
    void malformedJobIdIsNotFound() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());

        mockMvc.perform(get("/api/v1/jobs/not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("JOB_NOT_FOUND"));
    }
}
