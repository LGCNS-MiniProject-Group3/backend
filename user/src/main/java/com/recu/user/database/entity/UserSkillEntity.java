package com.recu.user.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_skill")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSkillEntity {

  @EmbeddedId private UserSkillPk id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  private UserEntity user;

  // 기술은 content 서비스의 표라 번호(id.techTagId)로만 갖는다. 원본은 @MapsId @ManyToOne TechTagEntity

  @Column(name = "skill_type", nullable = false, length = 10)
  private String skill_type; // HAVE 또는 WANT
}
