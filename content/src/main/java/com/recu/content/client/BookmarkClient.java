package com.recu.content.client;

import com.recu.content.client.dto.BookmarkLookupRequestDTO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "bookmark", path = "/internal/bookmarks")
public interface BookmarkClient {
  @PostMapping("/lookup")
  List<Long> lookup(@RequestBody BookmarkLookupRequestDTO request);
}
