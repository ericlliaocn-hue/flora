package com.pilipala.api.project.application;

/**
 * 删除结果。字段形状与素材删除一致：{@code {"id": "...", "deleted": true}}。
 */
public record DeleteProjectView(String id, boolean deleted) {
}
