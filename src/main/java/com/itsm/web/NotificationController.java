package com.itsm.web;

import java.util.List;

import com.itsm.domain.Notification;
import com.itsm.repository.NotificationRepository;
import com.itsm.security.ItsmUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/** The bell: each signed-in user's own notifications, whatever their role. */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        notification.markRead();
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
        return "redirect:/notifications";
    }
}
