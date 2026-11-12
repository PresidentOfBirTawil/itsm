package com.itsm.report;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.domain.SlaStatus;
import com.itsm.domain.Status;
import com.itsm.domain.Technician;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out the administrator's reports from the incidents. At the size of
 * this application it is simplest, and plenty fast, to load the incidents
 * once and count them in memory.
 */
@Service
public class ReportService {

    private final IncidentRepository incidents;
    private final TechnicianRepository technicians;
    private final SlaPolicyRepository slaPolicies;

    public ReportService(IncidentRepository incidents, TechnicianRepository technicians,
                         SlaPolicyRepository slaPolicies) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.slaPolicies = slaPolicies;
    }

    @Transactional(readOnly = true)
    public Reports build() {
        List<Incident> all = incidents.findAllByOrderByCreatedAtDescIdDesc();
        Map<Priority, Integer> targets = slaPolicies.findAll().stream()
                .collect(Collectors.toMap(SlaPolicy::getPriority, SlaPolicy::getResolutionMinutes));

        List<Reports.Count> byStatus = Arrays.stream(Status.values())
                .map(status -> new Reports.Count(status.getLabel(), count(all, i -> i.getStatus() == status)))
                .toList();
        List<Reports.Count> byPriority = Arrays.stream(Priority.values())
                .map(priority -> new Reports.Count(priority + " · " + priority.getLabel(),
                        count(all, i -> i.getPriority() == priority)))
                .toList();
        List<Reports.Count> byCategory = all.stream()
                .filter(incident -> incident.getCategory() != null)
                .collect(Collectors.groupingBy(incident -> incident.getCategory().getName(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new Reports.Count(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(Reports.Count::count).reversed().thenComparing(Reports.Count::label))
                .toList();

        List<Reports.ResolutionTime> resolutionTimes = Arrays.stream(Priority.values())
                .map(priority -> resolutionTime(priority, all, targets.get(priority)))
                .toList();

        List<Reports.TechnicianLine> lines = technicians.findAllByOrderByNameAsc().stream()
                .map(technician -> line(technician, all))
                .toList();

        OptionalDouble averageRating = all.stream().map(Incident::getRating).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average();

        return new Reports(all.size(), byStatus, byPriority, byCategory,
                count(all, i -> i.getSlaStatus() == SlaStatus.MET),
                count(all, Incident::isSlaBreached),
                resolutionTimes, lines,
                averageRating.isPresent() ? averageRating.getAsDouble() : null,
                count(all, i -> i.getRating() != null));
    }

    private static Reports.ResolutionTime resolutionTime(Priority priority, List<Incident> all, Integer target) {
        List<Long> minutes = all.stream()
                .filter(incident -> incident.getPriority() == priority && incident.getResolvedAt() != null)
                // Time the clock stood still (waiting for information) does not count as time taken.
                .map(incident -> Duration.between(incident.getCreatedAt(), incident.getResolvedAt()).toMinutes()
                        - incident.getSlaPausedSeconds() / 60)
                .toList();
        long average = minutes.isEmpty() ? 0 : Math.round(minutes.stream().mapToLong(Long::longValue).average().orElse(0));
        return new Reports.ResolutionTime(priority, minutes.size(), average, target);
    }

    private static Reports.TechnicianLine line(Technician technician, List<Incident> all) {
        List<Incident> theirs = all.stream().filter(incident -> technician.equals(incident.getAssignee())).toList();
        OptionalDouble rating = theirs.stream().map(Incident::getRating).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average();
        return new Reports.TechnicianLine(technician.getName(), theirs.size(),
                count(theirs, incident -> incident.getStatus().isOpen()),
                count(theirs, incident -> !incident.getStatus().isOpen()),
                count(theirs, Incident::isSlaBreached),
                rating.isPresent() ? rating.getAsDouble() : null);
    }

    private static long count(List<Incident> incidents, Function<Incident, Boolean> test) {
        return incidents.stream().filter(test::apply).count();
    }
}
