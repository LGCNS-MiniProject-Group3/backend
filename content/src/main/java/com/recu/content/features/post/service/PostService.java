package com.recu.content.features.post.service;

import com.recu.content.client.ContentBookmarkLookup;
import com.recu.content.features.post.domain.dto.PostResponseDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {
  private final PostQueryService queries;
  private final ContentBookmarkLookup bookmarkLookup;

  public PostResponseDTO getPost(Long id, Long userId) {
    PostResponseDTO post = queries.getPost(id);
    boolean bookmarked = bookmarkLookup.get(userId, List.of(post.postId())).contains(post.postId());
    return new PostResponseDTO(
        post.postId(),
        post.title(),
        post.publishedAt(),
        post.companyName(),
        post.categories(),
        post.skills(),
        post.charCount(),
        post.originalUrl(),
        post.contentHtml(),
        post.sections(),
        bookmarked);
  }
}
