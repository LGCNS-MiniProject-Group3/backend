package com.recu.collector.features.collect.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.collector.features.collect.domain.dto.ImportedPost;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 본문을 소제목으로 자르는 규칙. 수집 단계 파이썬과 같은 결과가 나와야 한다. */
class SectionSplitterTest {

  private final SectionSplitter splitter = new SectionSplitter();

  private ImportedPost post(String body, List<ImportedPost.Heading> headings) {
    return new ImportedPost(
        "우아한형제들",
        null,
        "제목",
        "https://example.com/1",
        "2026-09-18",
        100,
        true,
        List.of(),
        headings,
        "<h2>x</h2>",
        body,
        null);
  }

  @Test
  @DisplayName("소제목 사이가 구간 본문이 되고 seq 는 1부터 붙는다")
  void 소제목_사이를_자른다() {
    List<SectionSplitter.Section> sections =
        splitter.split(
            post(
                "들어가며 첫 문단입니다. 문제 상황 두 번째 문단입니다. 마치며 끝인사입니다.",
                List.of(
                    new ImportedPost.Heading(1, "h2", "들어가며"),
                    new ImportedPost.Heading(2, "h2", "문제 상황"),
                    new ImportedPost.Heading(3, "h2", "마치며"))));

    assertThat(sections).hasSize(3);
    assertThat(sections.get(0).seq()).isEqualTo(1);
    assertThat(sections.get(0).heading()).isEqualTo("들어가며");
    assertThat(sections.get(0).contentText()).isEqualTo("첫 문단입니다.");
    assertThat(sections.get(1).contentText()).isEqualTo("두 번째 문단입니다.");
    assertThat(sections.get(2).contentText()).isEqualTo("끝인사입니다.");
  }

  @Test
  @DisplayName("같은 문구가 본문에 또 나와도 순서가 꼬이지 않는다")
  void 같은_문구가_반복돼도_순서를_지킨다() {
    List<SectionSplitter.Section> sections =
        splitter.split(
            post(
                "정리 앞부분입니다. 정리 뒷부분입니다.",
                List.of(
                    new ImportedPost.Heading(1, "h2", "정리"),
                    new ImportedPost.Heading(2, "h2", "정리"))));

    assertThat(sections).hasSize(2);
    assertThat(sections.get(0).contentText()).isEqualTo("앞부분입니다.");
    assertThat(sections.get(1).contentText()).isEqualTo("뒷부분입니다.");
  }

  @Test
  @DisplayName("본문에서 못 찾은 소제목은 건너뛴다")
  void 못_찾은_소제목은_건너뛴다() {
    List<SectionSplitter.Section> sections =
        splitter.split(
            post(
                "들어가며 첫 문단입니다.",
                List.of(
                    new ImportedPost.Heading(1, "h2", "들어가며"),
                    new ImportedPost.Heading(2, "h2", "본문에 없는 제목"))));

    assertThat(sections).hasSize(1);
    assertThat(sections.get(0).seq()).isEqualTo(1);
  }

  @Test
  @DisplayName("소제목이 없으면 구간을 만들지 않는다")
  void 소제목이_없으면_빈_목록() {
    assertThat(splitter.split(post("본문만 있습니다.", List.of()))).isEmpty();
    assertThat(splitter.split(post("본문만 있습니다.", null))).isEmpty();
  }
}
