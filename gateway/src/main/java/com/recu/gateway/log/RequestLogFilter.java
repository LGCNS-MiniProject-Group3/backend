package com.recu.gateway.log;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 요청마다 메서드, 경로, 상태, 걸린 시간을 한 줄로 남긴다. 가장 바깥에서 돌아 Gateway가 거절한 401·403도 남는다.
 *
 * <p>쿼리 문자열과 헤더(토큰)는 남기지 않는다.
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long start = System.currentTimeMillis();
    ServerHttpRequest request = exchange.getRequest();
    return chain
        .filter(exchange)
        .doFinally(
            signal ->
                log.info(
                    "{} {} → {} ({}ms)",
                    request.getMethod(),
                    request.getPath().value(),
                    exchange.getResponse().getStatusCode(),
                    System.currentTimeMillis() - start));
  }
}
