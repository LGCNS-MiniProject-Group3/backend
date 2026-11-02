package com.recu.collector.client;

import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "content", path = "/internal")
public interface ContentClient {

  @GetMapping("/companies")
  List<CompanyTarget> getCompanies();

  @GetMapping("/posts/urls")
  List<String> getCollectedUrls(@RequestParam("companyId") Long companyId);
}
