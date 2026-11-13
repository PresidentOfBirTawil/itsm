package com.itsm.live;

import com.itsm.security.ItsmUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The address every signed-in page connects to for live updates:
 * {@code GET /live}, answered with a stream of server-sent events that stays
 * open (see {@link LiveUpdates}).
 *
 * <p>This controller is deliberately outside {@code com.itsm.web}: the values
 * that are added to every page there (the bell count, for one) are read from
 * the database, and a request that stays open for hours must not keep a
 * database connection for itself. This request reads nothing from the
 * database: who is asking comes from the session.</p>
 */
@Controller
public class LiveController {

    private final LiveUpdates liveUpdates;

    public LiveController(LiveUpdates liveUpdates) {
        this.liveUpdates = liveUpdates;
    }

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live(@AuthenticationPrincipal ItsmUserDetails user, HttpSession session) {
        return liveUpdates.connect(user.getId(), user.getRole(), session.getId());
    }
}
