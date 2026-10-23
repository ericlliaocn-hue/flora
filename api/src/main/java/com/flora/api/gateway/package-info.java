/**
 * 预留：模型网关边界。
 *
 * <p>只允许 hop，不允许 fork：供应商差异由 Anytoken 消化，flora 侧只认内部 DTO。
 * 禁止在这里内嵌 NewAPI 或 qfzy-api，禁止在这里出现第二套用户 / 配额账本。
 *
 * <p>WP1 阶段只有配置边界（见 {@code application.yml} 的 {@code flora.gateway}），
 * 不含任何调用实现。
 */
package com.flora.api.gateway;
