package com.recu.content.messaging;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 외부 Kafka·MariaDB 검증은 접속 설정과 실행 플래그를 명시한 경우에만 실행한다. */
@EnabledIfSystemProperty(named = "recu.local-integration", matches = "true")
class GuideFlagListenerLocalIntegrationTest extends AbstractGuideFlagListenerTest {}
