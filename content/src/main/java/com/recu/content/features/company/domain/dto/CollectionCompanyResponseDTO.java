package com.recu.content.features.company.domain.dto;

public record CollectionCompanyResponseDTO(
    Long companyId, String name, String feedUrl, String feedType) {}
