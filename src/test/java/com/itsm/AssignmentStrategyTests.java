package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import com.itsm.assignment.AssignmentStrategy;
import com.itsm.assignment.LeastLoadedStrategy;
import com.itsm.assignment.RoundRobinStrategy;
import com.itsm.assignment.SkillBasedStrategy;
import com.itsm.assignment.TechnicianLoad;
import com.itsm.error.TechnicianUnavailableException;
import com.itsm.domain.Category;
import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Technician;
import com.itsm.domain.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The three assignment strategies, tested on their own with plain objects (no database). */
class AssignmentStrategyTests {

    private final Category network = Fixtures.category("Network");
    private final Category hardware = Fixtures.category("Hardware");
    private final Category database = Fixtures.category("Database");
    private final Category security = Fixtures.category("Security");

    // The same shape as the demo data: ids are the round-robin turn order.
    private Technician ravi;
    private Technician meera;
    private Technician arjun;
    private Technician sara;

    @BeforeEach
    void createTechnicians() {
        ravi = Fixtures.technician(1, "Ravi Kumar", 5, network, hardware);
        meera = Fixtures.technician(2, "Meera Nair", 4, database);
        arjun = Fixtures.technician(3, "Arjun Singh", 3, security, network);
        sara = Fixtures.technician(4, "Sara Thomas", 5);
    }

    private Incident incidentAbout(Category category) {
        return Fixtures.incident(Impact.MEDIUM, Urgency.MEDIUM, Fixtures.service(2), category);
    }

    private static TechnicianLoad load(Technician technician, long openIncidents) {
        return new TechnicianLoad(technician, openIncidents);
    }

    // ---- rules every strategy shares ----

    static Stream<AssignmentStrategy> everyStrategy() {
        return Stream.of(new SkillBasedStrategy(), new LeastLoadedStrategy(), new RoundRobinStrategy());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStrategy")
    void anUnavailableTechnicianIsNeverChosen(AssignmentStrategy strategy) {
        ravi.setAvailable(false); // Ravi would otherwise win under every strategy: skilled, idle, first in turn

        Technician chosen = strategy.choose(incidentAbout(network), List.of(load(ravi, 0), load(arjun, 2)));

        assertThat(chosen).isSameAs(arjun);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStrategy")
    void aTechnicianAtMaximumWorkloadIsNeverChosen(AssignmentStrategy strategy) {
        // Arjun's maximum is 3 and he has 3. Ravi has more open incidents but still has room.
        Technician chosen = strategy.choose(incidentAbout(network), List.of(load(arjun, 3), load(ravi, 4)));

        assertThat(chosen).isSameAs(ravi);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStrategy")
    void whenNobodyCanTakeMoreItThrows(AssignmentStrategy strategy) {
        ravi.setAvailable(false);
        List<TechnicianLoad> nobodyFree = List.of(load(ravi, 0), load(meera, 4), load(arjun, 3), load(sara, 5));

        assertThatThrownBy(() -> strategy.choose(incidentAbout(network), nobodyFree))
                .isInstanceOf(TechnicianUnavailableException.class)
                .hasMessageContaining("unavailable or already at their maximum workload");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStrategy")
    void withNoTechniciansAtAllItThrows(AssignmentStrategy strategy) {
        assertThatThrownBy(() -> strategy.choose(incidentAbout(network), List.of()))
                .isInstanceOf(TechnicianUnavailableException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStrategy")
    void everyStrategyHasAKeyANameAndADescription(AssignmentStrategy strategy) {
        assertThat(strategy.getKey()).matches("[a-z-]+");
        assertThat(strategy.getName()).isNotBlank();
        assertThat(strategy.getDescription()).isNotBlank();
    }

    // ---- skill-based ----

    @Nested
    class SkillBased {

        private final SkillBasedStrategy strategy = new SkillBasedStrategy();

        @Test
        void choosesATechnicianWhoHasTheSkill() {
            Technician chosen = strategy.choose(incidentAbout(database),
                    List.of(load(ravi, 0), load(meera, 3), load(arjun, 0), load(sara, 0)));

            assertThat(chosen).isSameAs(meera); // the only one with Database, even though she is the busiest
        }

        @Test
        void amongSkilledTechniciansTheLeastLoadedWins() {
            Technician chosen = strategy.choose(incidentAbout(network),
                    List.of(load(ravi, 2), load(meera, 0), load(arjun, 1), load(sara, 0)));

            assertThat(chosen).isSameAs(arjun); // Ravi and Arjun have Network; Arjun has fewer open incidents
        }

        @Test
        void equalLoadsAreSettledByName() {
            Technician chosen = strategy.choose(incidentAbout(network), List.of(load(ravi, 1), load(arjun, 1)));

            assertThat(chosen).isSameAs(arjun); // "Arjun Singh" comes before "Ravi Kumar"
        }

        @Test
        void skipsASkilledTechnicianWhoIsFullAndTakesTheOtherOne() {
            Technician chosen = strategy.choose(incidentAbout(network),
                    List.of(load(ravi, 4), load(arjun, 3), load(sara, 0)));

            assertThat(chosen).isSameAs(ravi); // Arjun is at his maximum of 3
        }

        @Test
        void throwsWhenNobodyWithTheSkillCanTakeIt() {
            arjun.setAvailable(false); // the only technician with Security

            assertThatThrownBy(() -> strategy.choose(incidentAbout(security),
                    List.of(load(ravi, 0), load(meera, 0), load(arjun, 0), load(sara, 0))))
                    .isInstanceOf(TechnicianUnavailableException.class)
                    .hasMessageContaining("Security");
        }

        @Test
        void neverFallsBackToATechnicianWithoutTheSkill() {
            assertThatThrownBy(() -> strategy.choose(incidentAbout(Fixtures.category("Telephony")),
                    List.of(load(ravi, 0), load(meera, 0), load(arjun, 0), load(sara, 0))))
                    .isInstanceOf(TechnicianUnavailableException.class)
                    .hasMessageContaining("Telephony");
        }

        @Test
        void throwsWhenTheIncidentHasNoCategory() {
            assertThatThrownBy(() -> strategy.choose(incidentAbout(null), List.of(load(ravi, 0))))
                    .isInstanceOf(TechnicianUnavailableException.class)
                    .hasMessageContaining("no category");
        }
    }

    // ---- least loaded ----

    @Nested
    class LeastLoaded {

        private final LeastLoadedStrategy strategy = new LeastLoadedStrategy();

        @Test
        void choosesTheTechnicianWithTheFewestOpenIncidents() {
            Technician chosen = strategy.choose(incidentAbout(network),
                    List.of(load(ravi, 3), load(meera, 1), load(arjun, 2), load(sara, 4)));

            assertThat(chosen).isSameAs(meera);
        }

        @Test
        void ignoresSkills() {
            Technician chosen = strategy.choose(incidentAbout(security),
                    List.of(load(arjun, 2), load(sara, 0)));

            assertThat(chosen).isSameAs(sara); // Sara has no Security skill but the lightest load
        }

        @Test
        void equalLoadsAreSettledByName() {
            Technician chosen = strategy.choose(incidentAbout(network),
                    List.of(load(sara, 0), load(ravi, 0), load(meera, 0), load(arjun, 0)));

            assertThat(chosen).isSameAs(arjun);
        }

        @Test
        void skipsTheLeastLoadedIfTheyAreUnavailable() {
            meera.setAvailable(false);

            Technician chosen = strategy.choose(incidentAbout(network),
                    List.of(load(ravi, 3), load(meera, 0), load(arjun, 2)));

            assertThat(chosen).isSameAs(arjun);
        }
    }

    // ---- round robin ----

    @Nested
    class RoundRobin {

        private final RoundRobinStrategy strategy = new RoundRobinStrategy();

        private List<TechnicianLoad> everyoneIdle() {
            return List.of(load(ravi, 0), load(meera, 0), load(arjun, 0), load(sara, 0));
        }

        @Test
        void takesTheTechniciansInTurnAndStartsAgainAfterTheLast() {
            Incident incident = incidentAbout(network);

            assertThat(strategy.choose(incident, everyoneIdle())).isSameAs(ravi);
            assertThat(strategy.choose(incident, everyoneIdle())).isSameAs(meera);
            assertThat(strategy.choose(incident, everyoneIdle())).isSameAs(arjun);
            assertThat(strategy.choose(incident, everyoneIdle())).isSameAs(sara);
            assertThat(strategy.choose(incident, everyoneIdle())).isSameAs(ravi);
        }

        @Test
        void theTurnOrderDoesNotDependOnTheOrderOfTheList() {
            List<TechnicianLoad> shuffled = List.of(load(sara, 0), load(arjun, 0), load(ravi, 0), load(meera, 0));

            assertThat(strategy.choose(incidentAbout(network), shuffled)).isSameAs(ravi);
            assertThat(strategy.choose(incidentAbout(network), shuffled)).isSameAs(meera);
        }

        @Test
        void skipsATechnicianWhoCannotTakeMoreAndGoesToTheNext() {
            meera.setAvailable(false);
            List<TechnicianLoad> loads = List.of(load(ravi, 0), load(meera, 0), load(arjun, 3), load(sara, 0));

            assertThat(strategy.choose(incidentAbout(network), loads)).isSameAs(ravi);
            assertThat(strategy.choose(incidentAbout(network), loads)).isSameAs(sara); // Meera away, Arjun full
            assertThat(strategy.choose(incidentAbout(network), loads)).isSameAs(ravi);
        }

        @Test
        void ignoresSkillsAndLoad() {
            Technician chosen = strategy.choose(incidentAbout(security),
                    List.of(load(ravi, 4), load(meera, 0), load(arjun, 0), load(sara, 0)));

            assertThat(chosen).isSameAs(ravi); // first in turn, although busiest and without the skill
        }
    }
}
