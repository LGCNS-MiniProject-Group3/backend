package com.recu.common.kafka.event;

/** C. recu.guide.created 내용물: 가이드를 새로 만든 사용자와 글 (design.md 6장). 키는 userId. */
public record GuideCreatedEvent(Long userId, Long postId) {}
