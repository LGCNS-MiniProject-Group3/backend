package com.recu.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 테스트에서는 Eureka에 등록하지 않는다 (discovery 없이 돌아가야 함)
@SpringBootTest(properties = "eureka.client.enabled=false")
class UserApplicationTests {

  @Test
  void contextLoads() {}
}
