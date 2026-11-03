package com.recu.user.messaging;

/** 프로필 등록·수정이 저장됐다는 서비스 안의 알림. 커밋된 뒤 {@link ProfileUpdatedPublisher}가 B로 보낸다. */
public record ProfileSaved(Long userId) {}
