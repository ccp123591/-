package com.fitcoach.social;

import com.fitcoach.exception.BusinessException;
import com.fitcoach.user.UserFollowRepository;
import com.fitcoach.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SocialControllerVisibilityTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final PostCommentRepository comments = mock(PostCommentRepository.class);
    private final PostLikeRepository likes = mock(PostLikeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserFollowRepository follows = mock(UserFollowRepository.class);
    private final SocialController controller = new SocialController(posts, comments, likes, users, follows);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void private_post_detail_and_comments_are_hidden_from_other_user() {
        authenticate(8L);
        Post post = post(1L, 7L, "PRIVATE");
        given(posts.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> controller.detail(1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.comments(1L)).isInstanceOf(BusinessException.class);
        verify(comments, never()).findByPostIdOrderByCreatedAtAsc(1L);
    }

    @Test
    void followers_post_is_visible_to_follower() {
        authenticate(8L);
        Post post = post(1L, 7L, "FOLLOWERS");
        given(posts.findById(1L)).willReturn(Optional.of(post));
        given(follows.existsByFollowerIdAndFollowingId(8L, 7L)).willReturn(true);
        given(comments.findByPostIdOrderByCreatedAtAsc(1L)).willReturn(List.of());

        assertThatCode(() -> controller.detail(1L)).doesNotThrowAnyException();
        assertThatCode(() -> controller.comments(1L)).doesNotThrowAnyException();
    }

    private static void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static Post post(Long id, Long userId, String visibility) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setVisibility(visibility);
        return post;
    }
}
