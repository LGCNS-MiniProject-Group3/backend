package com.recu.content;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.recu.common.web.error.BusinessException;
import com.recu.common.web.error.CommonErrorCode;
import com.recu.content.client.*;
import com.recu.content.client.dto.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentClientsTest {
  private final UserClient user = mock(UserClient.class);
  private final BookmarkClient bookmarks = mock(BookmarkClient.class);
  private final ContentUserPreference preference = new ContentUserPreference(user);
  private final ContentBookmarkLookup lookup = new ContentBookmarkLookup(bookmarks);

  @Test
  void preferencePreservesJobAndSkillNamesForExistingRecommendationLogic() {
    var response = new UserPreferenceResponseDTO(List.of("Backend"), List.of("Java", "Kafka"), 1);
    when(user.getPreference(12L)).thenReturn(response);
    assertThat(preference.get(12L)).isEqualTo(response);
    verify(user).getPreference(12L);
  }

  @Test
  void missingPreferenceFieldsAreAnUpstreamFailure() {
    when(user.getPreference(12L)).thenReturn(new UserPreferenceResponseDTO(null, List.of(), 1));
    assertThatThrownBy(() -> preference.get(12L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UPSTREAM_ERROR));
  }

  @Test
  void bookmarksAreQueriedOnceForTheWholePage() {
    var request = new BookmarkLookupRequestDTO(12L, List.of(1L, 2L, 3L));
    when(bookmarks.lookup(request)).thenReturn(List.of(1L, 3L));
    assertThat(lookup.get(12L, request.postIds())).containsExactlyInAnyOrder(1L, 3L);
    verify(bookmarks, times(1)).lookup(request);
  }

  @Test
  void unrelatedBookmarkIdsDoNotBecomeSavedFlags() {
    when(bookmarks.lookup(any())).thenReturn(List.of(999L));
    assertThat(lookup.get(12L, List.of(1L))).isEmpty();
  }

  @Test
  void emptyPageDoesNotCallBookmarkService() {
    assertThat(lookup.get(12L, List.of())).isEmpty();
    verifyNoInteractions(bookmarks);
  }
}
