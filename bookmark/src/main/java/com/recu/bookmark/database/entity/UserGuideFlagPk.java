package com.recu.bookmark.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserGuideFlagPk implements Serializable {
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "blog_post_id", nullable = false)
  private Long blogPostId;
}
