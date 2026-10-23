/**
 * 预留：worker 侧任务消费边界。
 *
 * <p>worker 保持瘦：读 job、调 Anytoken、写 step 与结果、ack / 重试 / DLQ。
 * 不在这里拉画布图、不做创作编排、不做计费确认。
 *
 * <p>WP1 阶段本包为空，RabbitMQ 消费者在 WP3 落地。
 */
package com.flora.worker.job;
