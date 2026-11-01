package com.recu.content.features.tech.ctrl;

import com.recu.content.features.post.domain.dto.ContentQueryParameters;
import com.recu.content.features.tech.domain.dto.InternalTechResponseDTO;
import com.recu.content.features.tech.service.TechService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tech-tags")
@RequiredArgsConstructor
public class InternalTechController {
  private final TechService service;

  @GetMapping
  @Operation(summary = "③ 기술 번호 일괄 조회")
  public List<InternalTechResponseDTO> getTechTags(
      @RequestParam(value = "ids", required = false) String ids) {
    return service.getInternalTechTags(ContentQueryParameters.idList(ids));
  }
}
