package com.recu.user.database.repository;

import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.entity.UserJobFieldEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJobFieldRepository extends JpaRepository<UserJobFieldEntity, Integer> {
  List<UserJobFieldEntity> findAllByUser(UserEntity user);
}
