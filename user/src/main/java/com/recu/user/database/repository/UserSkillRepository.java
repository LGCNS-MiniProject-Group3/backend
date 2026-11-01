package com.recu.user.database.repository;

import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.entity.UserSkillEntity;
import com.recu.user.database.entity.UserSkillPk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkillEntity, UserSkillPk> {
  // 칸 이름이 skill_type 이라 메서드 이름만으로는 조회가 안 만들어진다. 조회문을 직접 적는다
  @Query("select s from UserSkillEntity s where s.user = :user and s.skill_type = :skillType")
  List<UserSkillEntity> findAllByUserAndSkillType(
      @Param("user") UserEntity user, @Param("skillType") String skillType);
}
