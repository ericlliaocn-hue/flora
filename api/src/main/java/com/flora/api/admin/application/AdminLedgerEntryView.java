package com.flora.api.admin.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 运维后台看到的账本流水。
 *
 * @param type {@code topup} / {@code freeze} / {@code confirm} / {@code release}
 */
public record AdminLedgerEntryView(
        UUID id,
        UUID userId,
        String type,
        long amount,
        UUID holdId,
        UUID jobId,
        Instant createdAt) {
}
