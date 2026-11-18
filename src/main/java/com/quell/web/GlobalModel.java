package com.quell.web;

import com.quell.assignment.AssignmentService;
import com.quell.domain.Role;
import com.quell.repository.NotificationRepository;
import com.quell.security.QuellUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Values every page needs for its navbar, added to the model of every
 * controller in this package. (Not to the live-updates connection in
 * {@code com.quell.live}: that request stays open for hours and must not
 * read from the database.)
 */
@ControllerAdvice(basePackages = "com.quell.web")
public class GlobalModel {

    private final NotificationRepository notifications;
    private final AssignmentService assignmentService;

    public GlobalModel(NotificationRepository notifications, AssignmentService assignmentService) {
        this.notifications = notifications;
        this.assignmentService = assignmentService;
    }

    /** The number on the bell: the signed-in user's unread notifications. */
    @ModelAttribute("unreadNotifications")
    public long unreadNotifications(@AuthenticationPrincipal QuellUserDetails user) {
        return user == null ? 0 : notifications.countByRecipientIdAndReadAtIsNull(user.getId());
    }

    /** For administrators: how many incidents are waiting because no technician could be found. */
    @ModelAttribute("waitingCount")
    public long waitingCount(@AuthenticationPrincipal QuellUserDetails user) {
        return user != null && user.getRole() == Role.ADMIN ? assignmentService.countWaitingForTechnician() : 0;
    }
}
