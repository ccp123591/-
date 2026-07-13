package com.fitcoach.challenge;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.session.Session;
import com.fitcoach.session.SessionRepository;
import com.fitcoach.user.User;
import com.fitcoach.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChallengeServiceTest {

    @Mock ChallengeRepository challengeRepo;
    @Mock ChallengeParticipantRepository participantRepo;
    @Mock SessionRepository sessionRepo;
    @Mock UserRepository userRepo;

    @InjectMocks ChallengeService service;

    private Challenge active(long id, String action, int target) {
        return Challenge.builder()
                .id(id).title("挑战" + id).action(action).targetReps(target)
                .startDate(LocalDate.now().minusDays(1).toString())
                .endDate(LocalDate.now().plusDays(1).toString()).status("ACTIVE")
                .build();
    }

    @Test
    void listActive_only_returns_active_challenges() {
        given(challengeRepo.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                .willReturn(List.of(active(1L, "squat", 1000)));
        given(participantRepo.countByChallengeId(1L)).willReturn(42L);

        var list = service.listActive(null);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getParticipantCount()).isEqualTo(42L);
        assertThat(list.get(0).getJoined()).isNull(); // me=null → 不带个人字段
    }

    @Test
    void listActive_hides_expired_active_rows() {
        Challenge expired = active(1L, "squat", 1000);
        expired.setEndDate(LocalDate.now().minusDays(1).toString());
        given(challengeRepo.findByStatusOrderByCreatedAtDesc("ACTIVE")).willReturn(List.of(expired));

        assertThat(service.listActive(null)).isEmpty();
    }

    @Test
    void join_idempotent_when_already_registered() {
        Challenge c = active(1L, "squat", 1000);
        given(challengeRepo.findById(1L)).willReturn(Optional.of(c));
        given(participantRepo.existsByChallengeIdAndUserId(1L, 7L)).willReturn(true);
        given(sessionRepo.findByUserIdOrderBySessionDateDesc(7L)).willReturn(List.of());
        ChallengeParticipant existing = ChallengeParticipant.builder()
                .id(99L).challengeId(1L).userId(7L).progressReps(0).completed(false).build();
        given(participantRepo.findByChallengeIdAndUserId(1L, 7L)).willReturn(Optional.of(existing));

        service.join(7L, 1L);

        // 没创建新参与者，只通过 syncProgress 落回了同一行（id=99）
        verify(participantRepo, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.argThat(
                p -> p.getId() != null && p.getId().equals(99L)));
    }

    @Test
    void join_404_when_challenge_missing() {
        given(challengeRepo.findById(404L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.join(7L, 404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void join_400_when_challenge_ended() {
        Challenge c = active(1L, "squat", 1000);
        c.setStatus("ENDED");
        given(challengeRepo.findById(1L)).willReturn(Optional.of(c));
        assertThatThrownBy(() -> service.join(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束");
    }

    @Test
    void join_400_when_active_status_but_date_expired() {
        Challenge c = active(1L, "squat", 1000);
        c.setEndDate(LocalDate.now().minusDays(1).toString());
        given(challengeRepo.findById(1L)).willReturn(Optional.of(c));

        assertThatThrownBy(() -> service.join(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
        verify(participantRepo, never()).save(any());
    }

    @Test
    void onSessionCreated_aggregates_reps_within_window_and_marks_completed() {
        Challenge c = active(1L, "squat", 30);
        String start = LocalDate.now().minusDays(10).toString();
        String first = LocalDate.now().minusDays(5).toString();
        String second = LocalDate.now().minusDays(3).toString();
        String before = LocalDate.now().minusDays(11).toString();
        c.setStartDate(start);
        c.setEndDate(LocalDate.now().plusDays(10).toString());
        given(challengeRepo.findByStatusOrderByCreatedAtDesc("ACTIVE")).willReturn(List.of(c));
        given(participantRepo.existsByChallengeIdAndUserId(1L, 7L)).willReturn(true);
        given(participantRepo.findByChallengeIdAndUserId(1L, 7L))
                .willReturn(Optional.of(ChallengeParticipant.builder()
                        .id(11L).challengeId(1L).userId(7L).progressReps(0).completed(false).build()));

        // 三场 session：两场 squat（窗口内 20+15=35），一场 pushup（不计），一场过期 squat（窗口外，不计）
        Session s1 = mkSession(7L, "squat", 20, first);
        Session s2 = mkSession(7L, "squat", 15, second);
        Session s3 = mkSession(7L, "pushup", 50, second);
        Session s4 = mkSession(7L, "squat", 100, before); // before window
        given(sessionRepo.findByUserIdOrderBySessionDateDesc(7L)).willReturn(List.of(s1, s2, s3, s4));

        service.onSessionCreated(7L, "squat", 15);

        verify(participantRepo).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getProgressReps() == 35 && Boolean.TRUE.equals(p.getCompleted())));
    }

    @Test
    void onSessionCreated_ignores_action_not_matching_or_unregistered() {
        Challenge c = active(1L, "squat", 100);
        given(challengeRepo.findByStatusOrderByCreatedAtDesc("ACTIVE")).willReturn(List.of(c));
        given(participantRepo.existsByChallengeIdAndUserId(1L, 7L)).willReturn(false);

        service.onSessionCreated(7L, "pushup", 10);

        verify(participantRepo, never()).save(any(ChallengeParticipant.class));
    }

    @Test
    void rank_orders_by_progress_desc_and_attaches_user_meta() {
        Challenge c = active(1L, "squat", 100);
        given(challengeRepo.findById(1L)).willReturn(Optional.of(c));
        var p1 = ChallengeParticipant.builder().userId(10L).progressReps(80).completed(false).build();
        var p2 = ChallengeParticipant.builder().userId(20L).progressReps(50).completed(false).build();
        given(participantRepo.findByChallengeIdOrderByProgressRepsDesc(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 20), 2));
        given(userRepo.findAllById(any())).willReturn(List.of(
                User.builder().id(10L).nickname("Alice").build(),
                User.builder().id(20L).nickname("Bob").build()));

        var rows = service.rank(1L, 20);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRank()).isEqualTo(1);
        assertThat(rows.get(0).getNickname()).isEqualTo("Alice");
        assertThat(rows.get(0).getProgressReps()).isEqualTo(80);
    }

    private Session mkSession(Long uid, String action, int reps, String date) {
        Session s = new Session();
        s.setUserId(uid); s.setAction(action); s.setReps(reps);
        s.setSessionDate(date); s.setDuration(60);
        return s;
    }
}
