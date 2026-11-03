package com.recu.content.features.post.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record PostSummariesRequestDTO(@NotNull List<@NotNull @Positive Long> postIds) {}
