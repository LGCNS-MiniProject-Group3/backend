package com.recu.user.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.entity.UserJobFieldEntity;
import com.recu.user.database.entity.UserSkillEntity;
import com.recu.user.database.entity.UserSkillPk;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** 프로필 조회(GET /api/profile)가 쓰는 두 조회가 실제로 도는지. */
@DataJpaTest
@ActiveProfiles("test")
class ProfileRepositoryTest {

  @Autowired UserRepository users;
  @Autowired UserJobFieldRepository jobFields;
  @Autowired UserSkillRepository skills;

  @Test
  void 희망_직무와_보유_관심_기술을_사용자별로_읽는다() {
    UserEntity user =
        users.save(
            UserEntity.builder()
                .google_sub("test-sub")
                .name("테스트")
                .created_at(LocalDate.now())
                .updated_at(LocalDate.now())
                .build());
    // 기술은 content 서비스의 표라 번호만 쓴다 (원본은 TechTagEntity를 저장해 연결)
    Long kafka = 1L;
    Long redis = 2L;

    jobFields.save(UserJobFieldEntity.builder().user(user).jobField("Backend").build());
    jobFields.save(UserJobFieldEntity.builder().user(user).jobField("Data").build());
    skills.save(skill(user, kafka, "HAVE"));
    skills.save(skill(user, redis, "WANT"));

    assertThat(jobFields.findAllByUser(user))
        .extracting(UserJobFieldEntity::getJobField)
        .containsExactlyInAnyOrder("Backend", "Data");
    assertThat(skills.findAllByUserAndSkillType(user, "HAVE"))
        .extracting(s -> s.getId().getTechTagId())
        .containsExactly(kafka);
    assertThat(skills.findAllByUserAndSkillType(user, "WANT"))
        .extracting(s -> s.getId().getTechTagId())
        .containsExactly(redis);
  }

  private UserSkillEntity skill(UserEntity user, Long techTagId, String type) {
    return UserSkillEntity.builder()
        .id(new UserSkillPk(user.getUser_id(), techTagId))
        .user(user)
        .skill_type(type)
        .build();
  }
}
