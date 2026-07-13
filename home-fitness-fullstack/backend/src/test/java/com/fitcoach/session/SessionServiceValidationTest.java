package com.fitcoach.session;

import com.fitcoach.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SessionServiceValidationTest {
    @Test
    void create_rejects_invalid_session_date() {
        SessionRepository repository = mock(SessionRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<com.fitcoach.challenge.ChallengeService> challenges = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<com.fitcoach.user.ProfileExtractionService> profiles = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<com.fitcoach.infra.memory.VectorMemoryService> memory = mock(ObjectProvider.class);
        SessionService service = new SessionService(repository, challenges, profiles, memory);

        assertThatThrownBy(() -> service.create(7L, Map.of("sessionDate", "not-a-date")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
