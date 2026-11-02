package com.recu.common.kafka.event;

/** B. recu.profile.updated 내용물: 프로필이 바뀐 사용자 (design.md 6장). 키는 userId. */
public record ProfileUpdatedEvent(Long userId) {}
