package com.recu.content.client.dto;

import java.util.List;

public record BookmarkLookupRequestDTO(Long userId, List<Long> postIds) {}
