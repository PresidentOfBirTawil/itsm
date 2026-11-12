package com.itsm.service;

import com.itsm.domain.Category;
import com.itsm.domain.Employee;
import com.itsm.domain.ITService;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Urgency;
import com.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Component;

/**
 * The one place new incidents are made (the Factory pattern).
 *
 * <p>Making an incident takes more than calling a constructor: every part
 * must be present, it must start in status NEW, and it must receive the next
 * code in the series INC-0001, INC-0002, … Keeping all of that here means no
 * other code has to know those rules, and no incident can exist without a code.</p>
 */
@Component
public class IncidentFactory {

    private final IncidentRepository incidents;

    public IncidentFactory(IncidentRepository incidents) {
        this.incidents = incidents;
    }

    /**
     * Creates a new incident, ready to be prioritised and assigned.
     *
     * <p>The code comes from a database sequence, so two people reporting at
     * the same moment can never be given the same one. The sequence hands out
     * the next number when the incident is saved, and the incident turns it
     * into its code (number 1 becomes INC-0001).</p>
     */
    public Incident create(Employee reporter, String title, String description, ITService service,
                           Category category, Impact impact, Urgency urgency) {
        requirePresent(reporter, "reporter");
        requirePresent(service, "service");
        requirePresent(category, "category");
        requirePresent(impact, "impact");
        requirePresent(urgency, "urgency");

        Incident incident = new Incident(title, description, reporter, service, impact, urgency);
        incident.setCategory(category);
        return incidents.save(incident);
    }

    private static void requirePresent(Object part, String name) {
        if (part == null) {
            throw new IllegalArgumentException("An incident needs a " + name);
        }
    }
}
