package com.itsm.live;

import java.util.LinkedHashMap;
import java.util.Map;

import com.itsm.domain.Incident;
import com.itsm.event.IncidentEvent;
import com.itsm.event.IncidentEventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells the open pages when something happens to an incident. It is one more
 * listener for incident events, next to the audit trail and the
 * notifications (the Observer pattern): the code that changes incidents was
 * not touched to add live updates.
 *
 * <p>Two things keep the pages honest and quiet:</p>
 * <ul>
 *   <li>The news goes out only after the change has been committed. A page
 *       that fetches its content again must find the new state, and a change
 *       that is rolled back must not be announced at all.</li>
 *   <li>One user action often produces several events for the same incident
 *       (reported, prioritised, assigned). They are announced once.</li>
 * </ul>
 */
@Component
public class LiveUpdateListener implements IncidentEventListener {

    private final LiveUpdates liveUpdates;

    public LiveUpdateListener(LiveUpdates liveUpdates) {
        this.liveUpdates = liveUpdates;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        Incident incident = event.incident();
        String code = incident.getCode();
        Long reporterId = incident.getReporter().getId();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            liveUpdates.incidentChanged(code, reporterId); // no transaction to wait for
            return;
        }
        changedInThisTransaction().put(code, reporterId);
    }

    /** The incidents changed so far in the running transaction: code and reporter. Announced when it commits. */
    @SuppressWarnings("unchecked")
    private Map<String, Long> changedInThisTransaction() {
        Map<String, Long> changed = (Map<String, Long>) TransactionSynchronizationManager.getResource(this);
        if (changed == null) {
            Map<String, Long> fresh = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(this, fresh);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fresh.forEach(liveUpdates::incidentChanged);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(LiveUpdateListener.this);
                }
            });
            changed = fresh;
        }
        return changed;
    }
}
