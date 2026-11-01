package com.recu.content.database.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V1 read projection. Role 4 owns event-driven inserts and deletes. No V2 columns. */
@Entity
@Table(name = "user_guide_flag")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGuideFlagEntity {
  @EmbeddedId private UserGuideFlagPk id;
}
