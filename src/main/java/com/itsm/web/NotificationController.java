package com.itsm.web;

import java.util.List;

import com.itsm.domain.Notification;
import com.itsm.error.NotFoundException;
import com.itsm.live.LiveUpdates;
import com.itsm.repository.NotificationRepository;
import com.itsm.security.ItsmUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The bell: each signed-in user's own notifications, whatever their role. */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notifications;
    private final LiveUpdates liveUpdates;

    public NotificationController(NotificationRepository notifications, LiveUpdates liveUpdates) {
        this.notifications = notifications;
        this.liveUpdates = liveUpdates;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal ItsmUserDetails user, Model model) {
        model.addAttribute("notifications",
                notifications.findTop100ByRecipientIdOrderByCreatedAtDescIdDesc(user.getId()));
        return "notifications/list";
    }

    /** Marks one notification as read and goes to the incident it is about. */
    @GetMapping("/{id}")
    @Transactional
    public String open(@AuthenticationPrincipal ItsmUserDetails user, @PathVariable Long id) {
        Notification notification = notifications.findById(id)
                .filter(found -> found.getRecipient().getId().equals(user.getId())) // only your own
                .orElseThrow(() -> new NotFoundException("That notification does not exist."));
        notification.markRead();
        // Counting sends the change above to the database first, so this notification is no longer counted.
        updateBellInOtherTabs(user.getId(), notifications.countByRecipientIdAndReadAtIsNull(user.getId()));
        if (notification.getIncident() == null) {
            return "redirect:/notifications";
        }
        return "redirect:" + RolePaths.incidents(user.getRole()) + "/" + notification.getIncident().getCode();
    }

    @PostMapping("/read-all")
    @Transactional
    public String markAllRead(@AuthenticationPrincipal ItsmUserDetails user) {
        List<Notification> unread = notifications.findByRecipientIdAndReadAtIsNull(user.getId());
        unread.forEach(Notification::markRead);
        updateBellInOtherTabs(user.getId(), 0);
        return "redirect:/notifications";
    }

    /** Once the change is committed, the user's other open tabs show the new number on the bell. */
    private void updateBellInOtherTabs(Long userId, long unread) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                liveUpdates.unreadCount(userId, unread);
            }
        });
    }
}
