package com.recu.collector.features.collect.domain.dto;

/**
 * 수집 대상 기업. 원본은 content의 CompanyEntity를 직접 썼다. ⑥(GET /internal/companies) 응답을 받을 자리다.
 *
 * <p>필드는 원본 수집 코드가 읽던 값(이름, 피드 종류, 피드 주소)만 뒀다. ⑥을 정할 때 3번·5번이 확정한다 (확인 요망).
 */
public record CompanyTarget(Long companyId, String name, String feedType, String feedUrl) {}
