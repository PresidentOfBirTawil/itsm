package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.itsm.domain.Category;
import com.itsm.domain.ITService;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.SlaPolicy;
import com.itsm.intelligence.ServiceDetector;
import com.itsm.status.RootCause;
import com.itsm.status.RootCauseAnalyzer;
import com.itsm.status.ServiceGraph;
import com.itsm.status.ServiceHealth;
import com.itsm.status.ServiceMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Service intelligence as plain objects: the dependency graph, service health,
 * root-cause analysis, the layout of the dependency map and recognising a
 * service from a report. The services are the demo company's:
 *
 * <pre>
 *   Payment ──> Authentication ──> Database ──> Network
 *      │               └──────────────────────────^
 *      └──> Database, Network        Email ──> Authentication, Network
 * </pre>
 */
class ServiceIntelligenceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 12, 0);

    private ITService network;
    private ITService database;
    private ITService authentication;
    private ITService payment;
    private ITService email;
    private ServiceGraph graph;

    private static ITService service(long id, String name, int criticality) {
        ITService service = new ITService(name, criticality);
        ReflectionTestUtils.setField(service, "id", id);
        return service;
    }

    @BeforeEach
    void company() {
        network = service(1, "Corporate Network", 2);
        database = service(2, "Database Cluster", 3);
        authentication = service(3, "Authentication Service", 3);
        payment = service(4, "Payment Application", 3);
        email = service(5, "Email & Collaboration", 1);
        database.replaceDependencies(List.of(network));
        authentication.replaceDependencies(List.of(database, network));
        payment.replaceDependencies(List.of(database, authentication, network));
        email.replaceDependencies(List.of(authentication, network));
        graph = new ServiceGraph(List.of(network, database, authentication, payment, email));
    }

    private Incident incident(ITService service, Priority priority, long minutesAgo, String category) {
        Incident incident = Fixtures.incidentReportedAt(NOW.minusMinutes(minutesAgo));
        ReflectionTestUtils.setField(incident, "service", service);
        incident.setCategory(new Category(category));
        incident.prioritise(Fixtures.assessment(priority), new SlaPolicy(priority, 60, 480));
        return incident;
    }

    // ---- the graph ----

    @Test
    void upstreamIsEverythingAServiceNeedsDirectlyOrNot() {
        assertThat(graph.upstream(email)).containsExactlyInAnyOrder(authentication, database, network);
        assertThat(graph.upstream(network)).isEmpty();
    }

    @Test
    void downstreamIsEverythingThatNeedsAService() {
        assertThat(graph.downstream(database)).containsExactlyInAnyOrder(authentication, payment, email);
        assertThat(graph.downstream(payment)).isEmpty();
    }

    @Test
    void aLoopIsFoundBeforeItIsMade() {
        // Network needs nothing; if it needed Payment, Payment would wait for Network and Network for Payment.
        assertThat(ServiceGraph.loopIfDependsOn(network, List.of(payment))).contains(payment);
        assertThat(ServiceGraph.loopIfDependsOn(database, List.of(email))).contains(email);
        assertThat(ServiceGraph.loopIfDependsOn(email, List.of(database))).isEmpty();
        assertThat(ServiceGraph.loopIfDependsOn(email, List.of(email))).contains(email);
    }

    @Test
    void layersPutInfrastructureAtTheBottomAndEveryArrowPointsDown() {
        Map<ITService, Integer> layers = graph.layers();

        assertThat(layers.get(network)).isZero();
        assertThat(layers.get(database)).isEqualTo(1);
        assertThat(layers.get(authentication)).isEqualTo(2);
        assertThat(layers.get(payment)).isEqualTo(3);
        for (ITService service : graph.services()) {
            service.getDependsOn().forEach(dependency ->
                    assertThat(layers.get(dependency)).isLessThan(layers.get(service)));
        }
    }

    @Test
    void theMapDrawsEveryServiceAndEveryDependencyDownwards() {
        ServiceMap map = ServiceMap.of(graph, Map.of(), 960);

        assertThat(map.nodes()).hasSize(5);
        assertThat(map.edges()).hasSize(8);
        assertThat(map.edges()).allSatisfy(edge -> assertThat(edge.y2()).isGreaterThan(edge.y1()));
        assertThat(map.nodes()).allSatisfy(node -> {
            assertThat(node.x()).isBetween(0, 960 - node.width());
            assertThat(node.y()).isBetween(0, map.height() - node.height());
        });
    }

    // ---- health ----

    @Test
    void anOpenIncidentSaysHowItsServiceIsDoing() {
        assertThat(ServiceHealth.of(incident(payment, Priority.P1, 5, "Application"))).isEqualTo(ServiceHealth.MAJOR_OUTAGE);
        assertThat(ServiceHealth.of(incident(payment, Priority.P2, 5, "Application"))).isEqualTo(ServiceHealth.PARTIAL_OUTAGE);
        assertThat(ServiceHealth.of(incident(payment, Priority.P3, 5, "Application"))).isEqualTo(ServiceHealth.DEGRADED);
        assertThat(ServiceHealth.of(incident(payment, Priority.P4, 5, "Application"))).isEqualTo(ServiceHealth.OPERATIONAL);
        assertThat(ServiceHealth.DEGRADED.worst(ServiceHealth.MAJOR_OUTAGE)).isEqualTo(ServiceHealth.MAJOR_OUTAGE);
        assertThat(ServiceHealth.PARTIAL_OUTAGE.isOutage()).isTrue();
        assertThat(ServiceHealth.DEGRADED.isOutage()).isFalse();
    }

    // ---- root cause ----

    private final RootCauseAnalyzer analyzer = new RootCauseAnalyzer();

    @Test
    void twoFailingServicesThatShareADependencyPointToIt() {
        List<Incident> open = List.of(
                incident(payment, Priority.P1, 20, "Application"),
                incident(email, Priority.P2, 15, "Software"));

        RootCause cause = analyzer.analyse(graph, open, NOW).orElseThrow();

        // Payment and Email both need Authentication (and through it Database and Network);
        // Authentication is the most specific service that explains both.
        assertThat(cause.service()).isEqualTo(authentication);
        assertThat(cause.affected()).containsExactlyInAnyOrder(payment, email);
        assertThat(cause.confirmed()).isFalse();
        assertThat(cause.explain()).contains("2 services that depend on Authentication Service");
    }

    @Test
    void aSuspectWithItsOwnSeriousIncidentIsConfirmed() {
        List<Incident> open = List.of(
                incident(payment, Priority.P1, 20, "Application"),
                incident(network, Priority.P2, 60, "Hardware"));

        RootCause cause = analyzer.analyse(graph, open, NOW).orElseThrow();

        assertThat(cause.service()).isEqualTo(network);
        assertThat(cause.confirmed()).isTrue();
        assertThat(cause.explain()).contains("including Corporate Network itself");
    }

    @Test
    void oneProblemMinorProblemsOldProblemsAndAttacksAreNotARootCause() {
        assertThat(analyzer.analyse(graph, List.of(incident(payment, Priority.P1, 5, "Application")), NOW)).isEmpty();
        assertThat(analyzer.analyse(graph, List.of(
                incident(payment, Priority.P3, 5, "Application"),
                incident(email, Priority.P4, 5, "Software")), NOW)).isEmpty();
        assertThat(analyzer.analyse(graph, List.of(
                incident(payment, Priority.P1, 5 * 60, "Application"),
                incident(email, Priority.P1, 4 * 60, "Software")), NOW)).isEmpty();
        assertThat(analyzer.analyse(graph, List.of(
                incident(payment, Priority.P1, 5, "Application"),
                incident(authentication, Priority.P1, 5, "Security")), NOW)).isEmpty();
    }

    @Test
    void servicesWithNothingInCommonHaveNoRootCause() {
        ITService printing = service(9, "Printing", 1);
        ITService canteen = service(10, "Canteen app", 1);
        ServiceGraph separate = new ServiceGraph(List.of(printing, canteen));
        List<Incident> open = new ArrayList<>(List.of(
                incident(printing, Priority.P1, 5, "Hardware"),
                incident(canteen, Priority.P1, 5, "Application")));

        assertThat(separate.downstream(printing)).isEmpty();
        assertThat(analyzer.analyse(separate, open, NOW)).isEmpty();
    }

    // ---- recognising the service ----

    @Test
    void theServiceIsRecognisedFromItsKeywords() {
        network.replaceKeywords(List.of("vpn", "wifi", "internet"));
        payment.replaceKeywords(List.of("checkout", "card", "payment"));
        ServiceDetector detector = new ServiceDetector();

        Optional<ServiceDetector.Detection> detection = detector.detect("The VPN and the wifi keep dropping", Set.of(network, payment));

        assertThat(detection).isPresent();
        assertThat(detection.get().service()).isEqualTo(network);
        assertThat(detection.get().matchedKeywords()).containsExactly("vpn", "wifi");
        assertThat(detector.detect("Customers cannot pay at checkout", Set.of(network, payment)).orElseThrow().service())
                .isEqualTo(payment);
        assertThat(detector.detect("Something odd", Set.of(network, payment))).isEmpty();
        assertThat(detector.detect("cardboard boxes", Set.of(network, payment))).isEmpty();   // whole words only
    }
}
