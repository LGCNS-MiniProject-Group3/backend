package com.recu.bookmark.features.bookmark.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** ② 요청. content의 BookmarkLookupRequestDTO와 같은 모양 (design.md 5장). */
public record BookmarkLookupRequestDTO(
    @NotNull @Positive Long userId, @NotNull List<@NotNull @Positive Long> postIds) {}
