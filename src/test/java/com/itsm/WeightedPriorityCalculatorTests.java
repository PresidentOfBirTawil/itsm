package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itsm.domain.Impact;
import com.itsm.domain.Incident;
import com.itsm.domain.Priority;
import com.itsm.domain.PriorityAssessment;
import com.itsm.domain.Urgency;
import com.itsm.priority.WeightedPriorityCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Score = impact × urgency × service criticality; 18–27 is P1, 9–17 P2, 4–8 P3, 1–3 P4.
 *
 * <p>Three whole numbers from 1 to 3 can never multiply to 17 (or to 5, 7, 10, …),
 * so the edges of the bands are tested on the score itself, and the full
 * calculation is tested with the closest scores it can really produce.</p>
 */
class WeightedPriorityCalculatorTests {

    private final WeightedPriorityCalculator calculator = new WeightedPriorityCalculator();

    // ---- the bands ----

    @Test
    void seventeenIsStillP2() {
        assertThat(calculator.priorityFor(17)).isEqualTo(Priority.P2);
    }

    @Test
    void eighteenIsP1() {
        assertThat(calculator.priorityFor(18)).isEqualTo(Priority.P1);
    }

    @ParameterizedTest(name = "score {0} is {1}")
    @CsvSource({
            "1, P4", "2, P4", "3, P4",
            "4, P3", "5, P3", "8, P3",
            "9, P2", "10, P2", "16, P2", "17, P2",
            "18, P1", "19, P1", "26, P1", "27, P1"})
    void everyBandStartsAndEndsWhereItShould(int score, Priority expected) {
        assertThat(calculator.priorityFor(score)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "score {0} is rejected")
    @ValueSource(ints = {-1, 0, 28, 100})
    void scoresOutsideOneToTwentySevenAreRejected(int score) {
        assertThatThrownBy(() -> calculator.priorityFor(score)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- the whole calculation ----

    @ParameterizedTest(name = "{0} impact × {1} urgency × criticality {2} = {3}, {4}")
    @CsvSource({
            "HIGH,   HIGH,   3, 27, P1",   // the highest possible score
            "HIGH,   HIGH,   2, 18, P1",   // exactly on the P1 edge
            "HIGH,   MEDIUM, 3, 18, P1",
            "HIGH,   MEDIUM, 2, 12, P2",   // the highest score below 18 that can occur
            "HIGH,   HIGH,   1,  9, P2",   // exactly on the P2 edge
            "MEDIUM, MEDIUM, 2,  8, P3",   // the highest score below 9
            "MEDIUM, MEDIUM, 1,  4, P3",   // exactly on the P3 edge
            "HIGH,   LOW,    1,  3, P4",   // the highest score below 4
            "LOW,    LOW,    1,  1, P4"})  // the lowest possible score
    void scoreIsImpactTimesUrgencyTimesCriticality(Impact impact, Urgency urgency, int criticality,
                                                   int expectedScore, Priority expectedPriority) {
        Incident incident = Fixtures.incident(impact, urgency, Fixtures.service(criticality),
                Fixtures.category("Network"));

        PriorityAssessment assessment = calculator.calculate(incident);

        assertThat(assessment.score()).isEqualTo(expectedScore);
        assertThat(assessment.priority()).isEqualTo(expectedPriority);
    }

    @Test
    void assessmentKeepsTheThreeNumbersBehindTheScore() {
        Incident incident = Fixtures.incident(Impact.HIGH, Urgency.MEDIUM, Fixtures.service(3),
                Fixtures.category("Network"));

        PriorityAssessment assessment = calculator.calculate(incident);

        assertThat(assessment.impactWeight()).isEqualTo(3);
        assertThat(assessment.urgencyWeight()).isEqualTo(2);
        assertThat(assessment.serviceCriticality()).isEqualTo(3);
        assertThat(assessment.formula()).isEqualTo("3 × 2 × 3 = 18");
    }

    @Test
    void everyCombinationOfInputsLandsInTheRightBand() {
        for (Impact impact : Impact.values()) {
            for (Urgency urgency : Urgency.values()) {
                for (int criticality = 1; criticality <= 3; criticality++) {
                    Incident incident = Fixtures.incident(impact, urgency, Fixtures.service(criticality),
                            Fixtures.category("Network"));

                    PriorityAssessment assessment = calculator.calculate(incident);

                    int score = impact.getWeight() * urgency.getWeight() * criticality;
                    assertThat(assessment.score()).isEqualTo(score);
                    assertThat(assessment.priority()).isEqualTo(
                            score >= 18 ? Priority.P1 : score >= 9 ? Priority.P2 : score >= 4 ? Priority.P3 : Priority.P4);
                }
            }
        }
    }

    @Test
    void theRuleIsDescribedInWords() {
        assertThat(calculator.describeRule())
                .contains("18–27 is P1", "9–17 is P2", "4–8 is P3", "1–3 is P4");
    }
}
