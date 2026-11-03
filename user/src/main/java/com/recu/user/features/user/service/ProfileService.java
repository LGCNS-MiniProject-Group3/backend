package com.recu.user.features.user.service;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.user.client.ContentClient;
import com.recu.user.client.dto.TechTagResponseDTO;
import com.recu.user.database.entity.UserEntity;
import com.recu.user.database.entity.UserJobFieldEntity;
import com.recu.user.database.entity.UserSkillEntity;
import com.recu.user.database.entity.UserSkillPk;
import com.recu.user.database.repository.UserJobFieldRepository;
import com.recu.user.database.repository.UserRepository;
import com.recu.user.database.repository.UserSkillRepository;
import com.recu.user.error.UserErrorCode;
import com.recu.user.features.user.domain.dto.ProfileRequestDTO;
import com.recu.user.features.user.domain.dto.ProfileResponseDTO;
import com.recu.user.features.user.domain.dto.ProfileResponseDTO.SkillResponseDTO;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService {

  private final UserRepository userRepository;

  private final UserJobFieldRepository userJobFieldRepository;
  private final UserSkillRepository userSkillRepository;
  private final ContentClient contentClient;

  @Transactional
  public ProfileResponseDTO setProfile(Long userId, ProfileRequestDTO request) {
    validateProfileRequest(request);

    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

    if (user.getProfile_version() != 0) {
      throw new BusinessException(UserErrorCode.PROFILE_ALREADY_EXISTS);
    }

    // app_user 저장
    // 입력받은 값을 DB에 저장
    userRepository.save(
        UserEntity.builder()
            .user_id(userId)
            .email(user.getEmail())
            .password_hash(user.getPassword_hash())
            .name(user.getName())
            .career_years(request.getCareerYears())
            .profile_version(1)
            .refresh_token(user.getRefresh_token())
            .role(user.getRole())
            .created_at(user.getCreated_at())
            .updated_at(LocalDate.now())
            .build());

    userJobFieldRepository.saveAll(
        request.getJobFields().stream()
            .map(jobField -> UserJobFieldEntity.builder().user(user).jobField(jobField).build())
            .toList());

    //////////////////// user_skill 저장 ////////////////////
    userSkillRepository.saveAll(
        request.getHaveSkillIds().stream()
            .map(
                haveSkillId ->
                    UserSkillEntity.builder()
                        .id(new UserSkillPk(userId, haveSkillId))
                        .user(user)
                        .skill_type("HAVE")
                        .build())
            .toList());

    userSkillRepository.saveAll(
        request.getWantSkillIds().stream()
            .map(
                wantSkillId ->
                    UserSkillEntity.builder()
                        .id(new UserSkillPk(userId, wantSkillId))
                        .user(user)
                        .skill_type("WANT")
                        .build())
            .toList());
    //////////////////// user_skill 저장 ////////////////////

    List<SkillResponseDTO> haveSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "HAVE").stream()
            .map(skill -> skill.getId().getTechTagId())
            .collect(collectingAndThen(toList(), this::skillsWithNames));

    List<SkillResponseDTO> wantSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "WANT").stream()
            .map(skill -> skill.getId().getTechTagId())
            .collect(collectingAndThen(toList(), this::skillsWithNames));

    return ProfileResponseDTO.builder()
        .name(user.getName())
        .userId(userId)
        .jobFields(request.getJobFields())
        .careerYears(request.getCareerYears())
        .haveSkills(haveSkills)
        .wantSkills(wantSkills)
        .profileVersion(1)
        .build();
  }

  @Transactional
  public ProfileResponseDTO getProfile(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

    List<String> jobFields =
        userJobFieldRepository.findAllByUser(user).stream()
            .map(UserJobFieldEntity::getJobField)
            .toList();

    List<SkillResponseDTO> haveSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "HAVE").stream()
            .map(skill -> skill.getId().getTechTagId())
            .collect(collectingAndThen(toList(), this::skillsWithNames));
    List<SkillResponseDTO> wantSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "WANT").stream()
            .map(skill -> skill.getId().getTechTagId())
            .collect(collectingAndThen(toList(), this::skillsWithNames));

    return ProfileResponseDTO.builder()
        .userId(user.getUser_id())
        .name(user.getName())
        .jobFields(jobFields)
        .careerYears(user.getCareer_years())
        .haveSkills(haveSkills)
        .wantSkills(wantSkills)
        .profileVersion(user.getProfile_version())
        .build();
  }

  @Transactional
  public ProfileResponseDTO editProfile(Long userId, ProfileRequestDTO request) {
    validateProfileRequest(request);

    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

    int nextProfileVersion = user.getProfile_version() + 1;

    // app_user 수정
    userRepository.save(
        UserEntity.builder()
            .user_id(userId)
            .email(user.getEmail())
            .password_hash(user.getPassword_hash())
            .name(user.getName())
            .career_years(request.getCareerYears())
            .profile_version(nextProfileVersion)
            .refresh_token(user.getRefresh_token())
            .role(user.getRole())
            .created_at(user.getCreated_at())
            .updated_at(LocalDate.now())
            .build());

    // user_job_field 수정
    userJobFieldRepository.deleteAll(userJobFieldRepository.findAllByUser(user));
    userJobFieldRepository.saveAll(
        request.getJobFields().stream()
            .map(jobField -> UserJobFieldEntity.builder().user(user).jobField(jobField).build())
            .toList());

    //////////////////// user_skill 수정 ////////////////////
    userSkillRepository.deleteAll(userSkillRepository.findAllByUserAndSkillType(user, "HAVE"));
    userSkillRepository.deleteAll(userSkillRepository.findAllByUserAndSkillType(user, "WANT"));

    userSkillRepository.flush();

    userSkillRepository.saveAll(
        request.getHaveSkillIds().stream()
            .map(
                haveSkillId ->
                    UserSkillEntity.builder()
                        .id(new UserSkillPk(userId, haveSkillId))
                        .user(user)
                        .skill_type("HAVE")
                        .build())
            .toList());

    userSkillRepository.saveAll(
        request.getWantSkillIds().stream()
            .map(
                wantSkillId ->
                    UserSkillEntity.builder()
                        .id(new UserSkillPk(userId, wantSkillId))
                        .user(user)
                        .skill_type("WANT")
                        .build())
            .toList());
    //////////////////// user_skill 수정 ////////////////////

    List<SkillResponseDTO> haveSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "HAVE").stream()
            .map(skill -> skill.getId().getTechTagId())
            .collect(collectingAndThen(toList(), this::skillsWithNames));

    List<SkillResponseDTO> wantSkills =
        userSkillRepository.findAllByUserAndSkillType(user, "WANT").stream()
            .map(skill -> skill.getId().getTechTagId())
            .collect(collectingAndThen(toList(), this::skillsWithNames));

    return ProfileResponseDTO.builder()
        .name(user.getName())
        .userId(userId)
        .jobFields(request.getJobFields())
        .careerYears(request.getCareerYears())
        .haveSkills(haveSkills)
        .wantSkills(wantSkills)
        .profileVersion(nextProfileVersion)
        .build();
  }

  private void validateProfileRequest(ProfileRequestDTO request) {
    // 희망 직무는 명세에 정의된 5개 계열 중 1개 이상이어야 한다.
    if (request.getJobFields() == null
        || request.getJobFields().isEmpty()
        || request.getJobFields().stream()
            .anyMatch(
                jobField ->
                    jobField == null
                        || !List.of("Backend", "Frontend", "Data", "Infra", "Mobile")
                            .contains(jobField))) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "jobFields");
    }

    // 보유 기술은 0개도 허용하지만 배열 자체는 필수다.
    if (request.getHaveSkillIds() == null || hasInvalidTechTagId(request.getHaveSkillIds())) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "haveSkillIds");
    }

    // 관심 기술은 추천 매칭에 사용되므로 1~5개만 허용한다.
    if (request.getWantSkillIds() == null
        || request.getWantSkillIds().isEmpty()
        || request.getWantSkillIds().size() > 5
        || hasInvalidTechTagId(request.getWantSkillIds())) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "wantSkillIds");
    }

    // null은 '미입력'으로 허용하고, 음수만 거부한다.
    if (request.getCareerYears() != null && request.getCareerYears() < 0) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "careerYears");
    }

    // 하나의 기술을 보유·관심 목록에 동시에 등록할 수 없다.
    if (request.getHaveSkillIds().stream().anyMatch(request.getWantSkillIds()::contains)) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT, "wantSkillIds");
    }
  }

  // 원본은 techTagRepository.existsById로 tech_tag 표를 직접 읽었다. 지금은 content의 ③으로 확인한다
  private boolean hasInvalidTechTagId(List<Long> techTagIds) {
    // content는 0 이하 번호를 400으로 막아서, 부르면 502가 된다. 부르기 전에 걸러 400으로 답한다
    if (techTagIds.stream().anyMatch(techTagId -> techTagId == null || techTagId <= 0)) {
      return true;
    }
    if (techTagIds.isEmpty()) {
      return false;
    }
    // ③은 없는 번호를 빼고 같은 번호는 한 번만 돌려준다. 받은 개수가 적으면 없는 번호가 섞인 것
    Set<Long> requested = new LinkedHashSet<>(techTagIds);
    return techTags(requested).size() < requested.size();
  }

  // 원본은 user_skill → tech_tag @ManyToOne으로 이름을 읽었다. 지금은 ③ 응답으로 채우고 넘겨받은 번호 순서를 지킨다
  private List<SkillResponseDTO> skillsWithNames(List<Long> techTagIds) {
    if (techTagIds.isEmpty()) {
      return List.of();
    }
    Map<Long, String> names =
        techTags(techTagIds).stream()
            .collect(Collectors.toMap(TechTagResponseDTO::id, TechTagResponseDTO::name));
    // content에서 지워진 기술은 이름을 알 수 없어 빼고 보여 준다
    return techTagIds.stream()
        .filter(names::containsKey)
        .map(techTagId -> new SkillResponseDTO(techTagId, names.get(techTagId)))
        .toList();
  }

  private List<TechTagResponseDTO> techTags(Collection<Long> techTagIds) {
    String ids = techTagIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    return contentClient.getTechTags(ids);
  }
}
