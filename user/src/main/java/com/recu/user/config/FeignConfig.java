package com.recu.user.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/** 내부 API를 부르는 Feign 클라이언트(client 패키지)를 켠다. 타임아웃·실패 처리는 common-web 기본값을 쓴다. */
@Configuration
@EnableFeignClients(basePackages = "com.recu.user.client")
public class FeignConfig {}
