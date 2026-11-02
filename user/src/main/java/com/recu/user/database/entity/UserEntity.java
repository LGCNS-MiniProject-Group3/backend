package com.recu.user.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long user_id;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String password_hash;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = true)
  @Min(0)
  private Integer career_years;

  @Builder.Default
  @Column(nullable = false)
  private Integer profile_version = 0;

  @Column(nullable = true)
  private String refresh_token;

  @Builder.Default
  @Column(nullable = false)
  private String role = "USER";

  @Column(nullable = false)
  private LocalDate created_at, updated_at;
}
