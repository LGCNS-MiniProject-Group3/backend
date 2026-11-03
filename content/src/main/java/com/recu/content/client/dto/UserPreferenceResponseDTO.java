package com.recu.content.client.dto;

import java.util.List;

public record UserPreferenceResponseDTO(
    List<String> jobFields, List<String> wantSkillNames, Integer profileVersion) {}
