package com.recu.content.features.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.content.database.entity.CompanyEntity;
import com.recu.content.database.repository.CompanyRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 기업 8곳 준비 때 소개 4칸과 로고가 들어가는지. 빈 DB와 이미 행이 있는 DB 둘 다 본다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanyIntroTest {

  @Autowired PostIngestService ingestService;
  @Autowired CompanyRepository companies;

  @Test
  void 빈_DB_에서_만든_기업_8곳은_소개와_로고를_가진다() {
    Map<String, CompanyEntity> byName = ingestService.ensureCompanies();

    assertThat(byName).hasSize(8);
    assertThat(byName.values())
        .allSatisfy(
            c -> {
              assertThat(c.getSummary()).isNotBlank();
              assertThat(c.getMain_business()).isNotBlank();
              assertThat(c.getSource_url()).startsWith("https://");
              assertThat(c.getChecked_at()).isNotNull();
              assertThat(c.getLogo_url()).startsWith("https://");
            });
  }

  @Test
  void 소개가_빈_기존_행은_채우고_사람이_넣은_값은_그대로_둔다() {
    companies.save(
        CompanyEntity.builder()
            .name("토스")
            .feed_url("https://toss.tech/rss.xml")
            .feed_type("FEED_LIST")
            .summary("사람이 고친 소개")
            .build());

    CompanyEntity toss = ingestService.ensureCompanies().get("토스");

    assertThat(toss.getSummary()).isEqualTo("사람이 고친 소개");
    assertThat(toss.getMain_business()).contains("토스뱅크");
    assertThat(toss.getSource_url()).isEqualTo("https://toss.tech");
    assertThat(toss.getLogo_url()).startsWith("https://static.toss.im/");
    assertThat(toss.getFeed_url()).isEqualTo("https://toss.tech/rss.xml");
    assertThat(companies.findAll().stream().filter(c -> c.getName().equals("토스"))).hasSize(1);
  }
}
