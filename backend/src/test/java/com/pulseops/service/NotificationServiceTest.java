package com.pulseops.service;

import com.pulseops.domain.notification.Notification;
import com.pulseops.domain.notification.NotificationType;
import com.pulseops.domain.user.User;
import com.pulseops.domain.user.UserRole;
import com.pulseops.dto.notification.NotificationPageResponse;
import com.pulseops.dto.notification.NotificationResponse;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldReturnRequestedPageAndUnreadCountWhileCappingPageSize() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        Notification first = notification(UUID.randomUUID(), user, "Incident opened", false);
        Notification second = notification(UUID.randomUUID(), user, "Deploy finished", true);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(7L);

        NotificationPageResponse result = notificationService.findForUser(userId, 2, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(userId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(result.unreadCount()).isEqualTo(7);
        assertThat(result.items()).extracting(NotificationResponse::title)
                .containsExactly("Incident opened", "Deploy finished");
    }

    @Test
    void shouldMarkOwnedNotificationAsReadAndPersistIt() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(UUID.randomUUID(), user(userId), "Incident opened", false);
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationResponse result = notificationService.markRead(userId, notification.getId());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(notification);
        assertThat(captor.getValue().isRead()).isTrue();
        assertThat(result.read()).isTrue();
    }

    @Test
    void shouldHideNotificationOwnedByAnotherUser() {
        UUID requestedUserId = UUID.randomUUID();
        Notification notification = notification(
                UUID.randomUUID(), user(UUID.randomUUID()), "Private notification", false);
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead(requestedUserId, notification.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(notification.getId().toString());

        assertThat(notification.isRead()).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldRejectMissingNotificationWithoutSaving() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(userId, notificationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(notificationId.toString());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldMarkEveryUnreadNotificationAndSaveBatchOnce() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        Notification first = notification(UUID.randomUUID(), user, "First", false);
        Notification second = notification(UUID.randomUUID(), user, "Second", false);
        List<Notification> unread = List.of(first, second);
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)).thenReturn(unread);

        notificationService.markAllRead(userId);

        assertThat(unread).allMatch(Notification::isRead);
        verify(notificationRepository).saveAll(unread);
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setName("PulseOps User");
        user.setEmail(id + "@pulseops.io");
        user.setPassword("encoded");
        user.setRole(UserRole.VIEWER);
        return user;
    }

    private Notification notification(UUID id, User user, String title, boolean read) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage("Notification details");
        notification.setType(NotificationType.INFO);
        notification.setRead(read);
        return notification;
    }
}
