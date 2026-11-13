package com.itsm.live;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import com.itsm.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Live updates: the list of browser tabs that are listening, and the three
 * kinds of news they can be sent. Each open page keeps one long-lived
 * connection to {@code /live} (server-sent events); this class pushes small
 * messages down those connections.
 *
 * <ul>
 *   <li>{@code incident}: an incident was created or changed. Sent to every
 *       administrator and technician (they may see every incident) and to the
 *       employee who reported it. It carries only the incident's code; the
 *       page then fetches its own fresh content, so what a user gets to see
 *       is still decided by the normal access rules.</li>
 *   <li>{@code bell}: a user's number of unread notifications. Sent to that user only.</li>
 *   <li>{@code refresh}: everything may have changed (the demo data was reset). Sent to everyone.</li>
 * </ul>
 *
 * <p>Messages are sent from one background thread, never from the thread
 * that is serving a request. A browser on a slow or broken connection can
 * therefore never hold up the person who caused the news.</p>
 *
 * <p>Nothing here touches the database, and nothing here can fail a
 * request: live updates are a convenience on top of pages that also work
 * without them.</p>
 */
@Component
public class LiveUpdates {

    /** How long a browser waits before it reconnects after losing the connection. */
    static final long RECONNECT_AFTER_MS = 5_000;

    private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);

    /** One browser tab that is listening. */
    private record Connection(Long userId, Role role, String sessionId, SseEmitter emitter) {
    }

    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final Executor sender;

    @Autowired
    public LiveUpdates() {
        this(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-updates");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** For tests: lets them send on the calling thread, so there is nothing to wait for. */
    public LiveUpdates(Executor sender) {
        this.sender = sender;
    }

    // ---- connecting and disconnecting ----

    /** Opens the connection for one browser tab. It stays open until the tab is closed. */
    public SseEmitter connect(Long userId, Role role, String sessionId) {
        SseEmitter emitter = new SseEmitter(0L); // no time limit: the keep-alive below notices dead connections
        register(userId, role, sessionId, emitter);
        return emitter;
    }

    /** Adds an open connection. Separate from {@link #connect} so that tests can hand in an emitter they can read. */
    public void register(Long userId, Role role, String sessionId, SseEmitter emitter) {
        Connection connection = new Connection(userId, role, sessionId, emitter);
        emitter.onCompletion(() -> connections.remove(connection));
        emitter.onTimeout(() -> connections.remove(connection));
        emitter.onError(error -> connections.remove(connection));
        connections.add(connection);
        // A first message, so that the browser knows the connection works and how long to wait before reconnecting.
        send(connection, SseEmitter.event().name("connected").reconnectTime(RECONNECT_AFTER_MS).data("{}"));
    }

    /** Closes every connection of a user, for example when their account is disabled. */
    public int disconnect(Long userId) {
        return close(connection -> connection.userId().equals(userId));
    }

    /** A user signed out or their session ended: the tabs of that session stop listening. */
    @EventListener
    public void onSessionEnded(HttpSessionDestroyedEvent event) {
        String sessionId = event.getId();
        close(connection -> sessionId.equals(connection.sessionId()));
    }

    /** The application is stopping: let go of the open connections so that it can stop at once. */
    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        close(connection -> true);
    }

    private int close(Predicate<Connection> which) {
        int closed = 0;
        for (Connection connection : connections) {
            if (which.test(connection) && connections.remove(connection)) {
                closed++;
                try {
                    connection.emitter().complete();
                } catch (RuntimeException e) {
                    log.debug("A live connection was already closed: {}", e.toString());
                }
            }
        }
        return closed;
    }

    /** How many browser tabs are listening right now. */
    public int connectionCount() {
        return connections.size();
    }

    // ---- the news ----

    /**
     * An incident was created or changed.
     *
     * @param code       the incident's code, e.g. INC-0007
     * @param reporterId the employee who reported it, who is told as well as the technicians and administrators
     */
    public void incidentChanged(String code, Long reporterId) {
        broadcast("incident", "{\"code\":\"" + code + "\"}",
                connection -> connection.role() != Role.EMPLOYEE || connection.userId().equals(reporterId));
    }

    /** A user's number of unread notifications changed. */
    public void unreadCount(Long userId, long unread) {
        broadcast("bell", "{\"unread\":" + unread + "}", connection -> connection.userId().equals(userId));
    }

    /** Everything may have changed: every open page fetches its content again. */
    public void everythingChanged() {
        broadcast("refresh", "{}", connection -> true);
    }

    /** Sent every few seconds. Keeps the connections open and finds the ones whose browser has gone. */
    @Scheduled(fixedRateString = "${itsm.live.keep-alive-ms:25000}")
    public void keepAlive() {
        if (!connections.isEmpty()) {
            sender.execute(() -> connections.forEach(
                    connection -> send(connection, SseEmitter.event().comment("keep-alive"))));
        }
    }

    private void broadcast(String name, String json, Predicate<Connection> to) {
        if (connections.isEmpty()) {
            return;
        }
        try {
            sender.execute(() -> connections.stream().filter(to)
                    .forEach(connection -> send(connection, SseEmitter.event().name(name).data(json))));
        } catch (RuntimeException e) {
            log.warn("A live update ({}) could not be queued: {}", name, e.toString());
        }
    }

    /** Sends one message down one connection. A connection that cannot be written to is forgotten. */
    private void send(Connection connection, SseEventBuilder message) {
        try {
            connection.emitter().send(message);
        } catch (IOException | RuntimeException e) {
            // The browser has gone. The server tidies up the connection itself; we only stop using it.
            connections.remove(connection);
            log.debug("Dropped a live connection of user {}: {}", connection.userId(), e.toString());
        }
    }
}
