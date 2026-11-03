package com.recu.collector.features.collect.service;

import com.recu.collector.client.ContentClient;
import com.recu.collector.features.collect.domain.dto.CompanyTarget;
import com.recu.collector.features.collect.domain.dto.ImportPublishResult;
import com.recu.collector.features.collect.domain.dto.ImportedPost;
import com.recu.collector.features.collect.domain.dto.TechDictionary;
import com.recu.collector.messaging.PostCollectedPublisher;
import com.recu.common.kafka.event.PostCollectedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 수집 단계가 만든 파일을 읽어 A(recu.post.collected)를 발행하는 입구.
 *
 * <p>파일에서 읽어 넘기는 일만 맡는다. 저장은 content가 메시지를 받아 한다.
 */
@Service
public class PostImportService {

  private static final Logger log = LoggerFactory.getLogger(PostImportService.class);

  private final ImportFileReader reader;
  private final ContentClient contentClient;
  private final PostCollectedEventMapper mapper;
  private final PostCollectedPublisher publisher;
  private final String dataDir;

  public PostImportService(
      ImportFileReader reader,
      ContentClient contentClient,
      PostCollectedEventMapper mapper,
      PostCollectedPublisher publisher,
      @Value("${collect.data-dir:}") String dataDir) {
    this.reader = reader;
    this.contentClient = contentClient;
    this.mapper = mapper;
    this.publisher = publisher;
    this.dataDir = dataDir;
  }

  public ImportPublishResult importFromFiles() {
    if (dataDir == null || dataDir.isBlank()) {
      throw new IllegalStateException("수집 파일 폴더가 정해지지 않았습니다. .env 에 COLLECT_DATA_DIR 을 적어 주세요.");
    }
    Path dir = Path.of(dataDir);
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException("그런 폴더가 없습니다: " + dir);
    }

    List<ImportedPost> rows = reader.readPosts(dir);
    TechDictionary dictionary = reader.readDictionary(dir);
    log.info("파일에서 글 {}편, 기술 사전 {}종을 읽었습니다", rows.size(), dictionary.finalCounts().size());

    TechNameResolver resolver = new TechNameResolver(dictionary);
    Map<String, Long> companyIdByName =
        contentClient.getCompanies().stream()
            .collect(Collectors.toMap(CompanyTarget::name, CompanyTarget::companyId, (a, b) -> a));

    int published = 0;
    for (ImportedPost row : rows) {
      Long companyId = companyIdByName.get(row.company());
      if (companyId == null) {
        log.warn("기업 ID를 못 찾아 건너뜀: company={}, url={}", row.company(), row.url());
        continue;
      }
      PostCollectedEvent event = mapper.toEvent(row, companyId, resolver);
      if (publisher.publish(event)) {
        published++;
      }
    }
    return new ImportPublishResult(published);
  }
}
