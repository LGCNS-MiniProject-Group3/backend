package com.recu.bookmark.client.dto;

import java.util.List;

/** ⑦ 요청. content의 PostSummariesRequestDTO와 같은 모양. */
public record PostSummariesRequestDTO(List<Long> postIds) {}
