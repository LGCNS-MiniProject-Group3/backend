package com.recu.content.messaging;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 외부 검증은 전용 Kafka·MariaDB 접속 설정과 실행 플래그를 명시한 경우에만 실행한다. */
@EnabledIfSystemProperty(named = "recu.post-integration", matches = "true")
class PostCollectedListenerLocalIntegrationTest extends AbstractPostCollectedListenerTest {}
