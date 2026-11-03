package com.recu.user.database.repository;

import com.recu.user.database.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByEmail(String email);

  // 엔티티 필드 이름에 밑줄이 있어 findByGoogleSub 으로는 못 만든다. JPQL 로 직접 적는다.

  @Modifying
  @Query("UPDATE UserEntity U set U.refresh_token = :token where U.user_id = :userId")
  int updateRefreshToken(Long userId, String token);
}
