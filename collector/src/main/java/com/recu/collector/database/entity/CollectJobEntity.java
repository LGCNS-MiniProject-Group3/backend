package com.recu.collector.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collect_job")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectJobEntity {

  @Id
  @Column(name = "job_id", length = 40, nullable = false)
  private String jobId;

  @Column(name = "company_id")
  private Long companyId;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Builder.Default
  @Column(name = "new_post_count", nullable = false)
  private int newPostCount = 0;

  // 상태 변경용 메서드
  public void markDone(int newPostCount, LocalDateTime finishedAt) {
    this.status = "DONE";
    this.newPostCount = newPostCount;
    this.finishedAt = finishedAt;
  }

  public void markFailed(LocalDateTime finishedAt) {
    this.status = "FAILED";
    this.finishedAt = finishedAt;
  }
}
