package com.recu.content;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.recu.common.web.CommonWebAutoConfiguration;
import com.recu.content.features.company.ctrl.InternalCompanyController;
import com.recu.content.features.company.domain.dto.CollectionCompanyResponseDTO;
import com.recu.content.features.company.service.CompanyService;
import com.recu.content.features.post.ctrl.InternalPostController;
import com.recu.content.features.post.domain.dto.InternalPostResponseDTO.*;
import com.recu.content.features.post.service.InternalPostService;
import com.recu.content.features.tech.ctrl.InternalTechController;
import com.recu.content.features.tech.domain.dto.InternalTechResponseDTO;
import com.recu.content.features.tech.service.TechService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
  InternalCompanyController.class,
  InternalPostController.class,
  InternalTechController.class
})
@ImportAutoConfiguration(CommonWebAutoConfiguration.class)
@ActiveProfiles("test")
class InternalContentControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean CompanyService companies;
  @MockitoBean InternalPostService posts;
  @MockitoBean TechService tech;

  @Test
  void techIdsAndNamesMatchDesign() throws Exception {
    when(tech.getInternalTechTags(List.of(1L, 2L)))
        .thenReturn(List.of(new InternalTechResponseDTO(1L, "Java")));
    mvc.perform(get("/internal/tech-tags").param("ids", "1,2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].name").value("Java"));
  }

  @Test
  void guideInputContainsSectionText() throws Exception {
    when(posts.getGuideInput(3L))
        .thenReturn(
            new GuideInput(
                3L, "Title", "Backend", "중급", List.of(new Section(1, "Heading", "Full body"))));
    mvc.perform(get("/internal/posts/3/guide-input"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.postId").value(3))
        .andExpect(jsonPath("$.sections[0].contentText").value("Full body"));
  }

  @Test
  void companyListContainsFeedSettings() throws Exception {
    when(companies.getCollectionCompanies())
        .thenReturn(
            List.of(
                new CollectionCompanyResponseDTO(
                    1L, "Alpha", "https://example.com/feed", "FEED_FULL")));
    mvc.perform(get("/internal/companies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].companyId").value(1))
        .andExpect(jsonPath("$[0].feedUrl").value("https://example.com/feed"))
        .andExpect(jsonPath("$[0].feedType").value("FEED_FULL"));
  }

  @Test
  void urlsAreQueriedByCompany() throws Exception {
    when(posts.getCollectedUrls(1L)).thenReturn(List.of("https://example.com/post"));
    mvc.perform(get("/internal/posts/urls").param("companyId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("https://example.com/post"));
  }

  @Test
  void summariesUseBatchRequestAndCardFields() throws Exception {
    when(posts.getSummaries(List.of(3L)))
        .thenReturn(
            List.of(
                new Summary(
                    3L,
                    1L,
                    "Alpha",
                    "Title",
                    List.of("Backend"),
                    "Summary",
                    List.of("Java"),
                    LocalDate.of(2026, 1, 2))));
    mvc.perform(
            post("/internal/posts/summaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postIds\":[3]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].companyName").value("Alpha"))
        .andExpect(jsonPath("$[0].skills[0]").value("Java"))
        .andExpect(jsonPath("$[0].publishedAt").value("2026-01-02"));
    verify(posts).getSummaries(List.of(3L));
  }

  @Test
  void invalidInternalIdsNeverReachDatabaseServices() throws Exception {
    mvc.perform(get("/internal/posts/urls")).andExpect(status().isBadRequest());
    mvc.perform(get("/internal/tech-tags").param("ids", "1,abc"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/internal/posts/abc/guide-input")).andExpect(status().isBadRequest());
    mvc.perform(
            post("/internal/posts/summaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postIds\":[0]}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(posts, tech, companies);
  }
}
