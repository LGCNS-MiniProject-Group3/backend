package com.recu.content.database.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1 가이드 표시 사본. 이벤트에 따른 추가와 삭제는 4번 담당자가 구현한다. */
@Entity
@Table(name = "user_guide_flag")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGuideFlagEntity {
  @EmbeddedId private UserGuideFlagPk id;
}
