package com.recu.bookmark.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "bookmark",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_bookmark",
          columnNames = {"user_id", "blog_post_id"})
    })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long bookmark_id;

  // 다른 서비스의 행은 번호로만 갖는다 (원본은 @ManyToOne). 컬럼 이름은 그대로라 테이블 모양은 같다
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "blog_post_id", nullable = false)
  private Long blogPostId;

  @Column(nullable = false)
  private LocalDateTime created_at;
}
