package com.recu.guide.client.dto;

import java.util.List;

/** ⑤ 응답. 구간은 seq 순서로 온다 (content의 InternalPostResponseDTO.GuideInput과 같은 모양). */
public record GuideInputResponseDTO(
    Long postId, String title, String field, String level, List<Section> sections) {

  public record Section(Integer seq, String heading, String contentText) {}
}
