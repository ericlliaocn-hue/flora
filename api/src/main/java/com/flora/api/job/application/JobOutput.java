package com.flora.api.job.application;

import java.util.List;

/**
 * result 的输出元素。
 *
 * @param type      如 image
 * @param objectKey 对象键；WP8 之前没有签名 URL
 * @param url       短期读地址；WP4 恒为 null
 */
public record JobOutput(String type, String objectKey, String url) {
}
