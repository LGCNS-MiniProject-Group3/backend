package com.recu.content.features.company.ctrl;

import com.recu.content.features.company.domain.dto.CollectionCompanyResponseDTO;
import com.recu.content.features.company.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/companies")
@RequiredArgsConstructor
public class InternalCompanyController {
  private final CompanyService service;

  @GetMapping
  @Operation(summary = "⑥ 수집 대상 기업과 수집 설정 조회")
  public List<CollectionCompanyResponseDTO> getCompanies() {
    return service.getCollectionCompanies();
  }
}
