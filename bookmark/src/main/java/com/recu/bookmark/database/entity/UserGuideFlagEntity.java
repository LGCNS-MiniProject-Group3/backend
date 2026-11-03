package com.recu.bookmark.database.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** "가이드 있음" 표시 사본. guide의 C·user의 B 메시지로만 바뀐다 (design.md 6장). content의 같은 표와 모양이 같다. */
@Entity
@Table(name = "user_guide_flag")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGuideFlagEntity {
  @EmbeddedId private UserGuideFlagPk id;
}
