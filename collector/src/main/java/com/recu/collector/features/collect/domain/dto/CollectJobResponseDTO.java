package com.recu.collector.features.collect.domain.dto;

import java.time.OffsetDateTime;

public record CollectJobResponseDTO(
    String jobId,
    Long companyId,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    int newPostCount) {}
