package com.pilipala.api.job.domain;

import java.util.Locale;

/**
 * 任务产物的类型判定：视频还是图片。
 *
 * <p>两个信号任一命中就是视频产物：任务能力（{@code video.generate}）或对象键后缀
 * （{@code .mp4}）。上游给的模型名不可靠，能力与后缀才是准的。
 *
 * <p>放在 job 域里：{@code GetJobResult} 与 {@code AssetService} 都要用它，
 * 两边各判一次会漂。
 */
public final class JobOutputs {

    /** 视频产物的对象键后缀。 */
    public static final String VIDEO_SUFFIX = ".mp4";

    private JobOutputs() {
    }

    /** 该任务的产物是不是视频。 */
    public static boolean isVideoJob(String capability, String objectKey) {
        if (capability != null && capability.toLowerCase(Locale.ROOT).startsWith("video")) {
            return true;
        }
        return objectKey != null && objectKey.toLowerCase(Locale.ROOT).endsWith(VIDEO_SUFFIX);
    }

    /** 产物的 MIME 类型：视频 {@code video/mp4}，图片 {@code image/png}。 */
    public static String mimeTypeOf(String capability, String objectKey) {
        return isVideoJob(capability, objectKey) ? "video/mp4" : "image/png";
    }

    /** 产物的输出类型：C 端 {@code GET /api/v1/jobs/{id}/result} 的 {@code outputs[].type}。 */
    public static String outputTypeOf(String capability, String objectKey) {
        return isVideoJob(capability, objectKey) ? "video" : "image";
    }
}
