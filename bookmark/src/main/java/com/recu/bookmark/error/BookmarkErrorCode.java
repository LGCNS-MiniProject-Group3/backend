package com.recu.bookmark.error;

import com.recu.common.web.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** bookmark 서비스에만 있는 오류 코드. 원본 ErrorCode에서 옮겼고 이름·메시지는 그대로다. 공통 코드는 CommonErrorCode. */
@Getter
@RequiredArgsConstructor
public enum BookmarkErrorCode implements ErrorCode {
  ALREADY_BOOKMARKED(HttpStatus.CONFLICT, "이미 북마크한 게시글입니다.");

  private final HttpStatus status;
  private final String message;
}
