package com.flora.api.job.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flora.api.job.domain.Job;
import com.flora.api.job.domain.JobStatus;
import com.flora.api.job.infra.JobErrorMessages;
import com.flora.api.job.infra.JobException;
import com.flora.api.job.infra.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 查询任务结果。
 *
 * <p>outputs 来自 {@code job.result_object_keys}；本 WP 没有 TOS 签名，
 * 所以每个元素的 {@code url} 恒为 null。
 */
@Service
public class GetJobResult {

    private static final Logger log = LoggerFactory.getLogger(GetJobResult.class);

    private final JobRepository jobs;
    private final ObjectMapper mapper;

    public GetJobResult(JobRepository jobs, ObjectMapper mapper) {
        this.jobs = jobs;
        this.mapper = mapper;
    }

    public JobResultView result(UUID userId, UUID jobId, String locale) {
        Job job = jobs.findByIdAndUser(jobId, userId).orElseThrow(JobException::notFound);
        List<JobOutput> outputs = job.status() == JobStatus.SUCCEEDED ? outputs(job) : List.of();
        String errorCode = job.errorCode();
        return new JobResultView(
                job.id().toString(),
                job.status().value(),
                outputs,
                errorCode,
                errorCode == null ? null : JobErrorMessages.resolve(errorCode, locale));
    }

    private List<JobOutput> outputs(Job job) {
        String json = job.resultObjectKeysJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<JobOutput> outputs = new ArrayList<>();
            mapper.readTree(json).forEach(item -> outputs.add(new JobOutput("image", item.asText(), null)));
            return outputs;
        } catch (JsonProcessingException e) {
            log.warn("result_object_keys 解析失败 job={}", job.id());
            return List.of();
        }
    }
}
