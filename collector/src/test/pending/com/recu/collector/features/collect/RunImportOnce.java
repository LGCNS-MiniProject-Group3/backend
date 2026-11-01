// 빌드에서 뺀 원본 테스트 (분리 이식 #7). 원본에서도 @Disabled였던 일회성 적재 도구. content PostIngestService를 직접 불러 분리 후
// 컴파일되지 않는다. TODO(A): 파일 적재가 A 발행으로 바뀌면 다시 쓴다.
// 담당자가 고친 뒤 src/test/java 아래 같은 경로로 되돌린다.
package com.recu.collector.features.collect;

import com.recu.collector.features.collect.service.PostImportService;
import com.recu.content.features.ingest.service.PostIngestService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 초기 402편을 실제 DB 에 넣는 일회성 실행.
 *
 * <p>평소에는 돌지 않는다({@code @Disabled}). 넣어야 할 때만 아래 명령으로 부른다.
 *
 * <pre>
 *   .\gradlew test --tests "*RunImportOnce*" -DrunImport=true
 * </pre>
 *
 * <p>되돌리지 않는다. 표에 그대로 남는다. 같은 글은 url 로 걸러내므로 여러 번 돌려도 안전하다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Disabled("일회성. 표에 넣을 때만 이 줄을 지우고 돌린다")
class RunImportOnce {

  @Autowired PostImportService importService;

  @Test
  void 파일에서_실제_DB_에_넣는다() {
    PostIngestService.IngestResult result = importService.importFromFiles();
    System.out.println("=====================================");
    System.out.println("  기업        " + result.companyCount());
    System.out.println("  기술 사전    " + result.techTagCount());
    System.out.println("  새 글       " + result.newPosts());
    System.out.println("  건너뜀      " + result.skipped());
    System.out.println("  구간        " + result.sectionCount());
    System.out.println("  태그        " + result.tagCount());
    System.out.println("=====================================");
  }
}
