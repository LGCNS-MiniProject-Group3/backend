package com.recu.gateway.log;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * Prometheus가 5초마다 부르는 /actuator/prometheus 같은 요청은 관측(추적·요청 지표)하지 않는다 (#119).
 *
 * <p>로컬은 모든 요청을 추적해서(샘플링 1.0) 이 요청이 모두 Zipkin trace로 쌓이고, 실제 사용자 요청의 trace를 찾기 어려웠다. 업무 서비스는
 * common-web에 같은 규칙이 있고, gateway는 WebFlux라 요청 객체가 달라서 여기 따로 둔다.
 */
@Configuration
public class ObservationConfig {

  @Bean
  ObservationPredicate actuatorObservationPredicate() {
    return (name, context) ->
        !(context instanceof ServerRequestObservationContext request
            && isActuatorPath(request.getCarrier().getPath().value()));
  }

  private static boolean isActuatorPath(String path) {
    return path.equals("/actuator") || path.startsWith("/actuator/");
  }
}
