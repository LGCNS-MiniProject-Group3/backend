package com.recu.common.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 서비스가 common-kafka 의존성만 추가하면 켜지는 설정 (design.md 6장).
 *
 * <p>업무 메시지의 발행·수신 설정과 토픽 생성을 여기에 둔다.
 */
@AutoConfiguration
public class CommonKafkaAutoConfiguration {}
