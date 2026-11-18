package com.quell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import com.quell.domain.Impact;
import com.quell.domain.Urgency;
import com.quell.intelligence.TriageAdvice;
import com.quell.intelligence.TriageAdvisor;
import com.quell.intelligence.text.NaiveBayesClassifier;
import com.quell.intelligence.text.NaiveBayesClassifier.Example;
import com.quell.intelligence.text.SimilarityIndex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The text methods behind the learning features: TF-IDF similarity, naive Bayes, smart triage. Plain objects. */
class TextIntelligenceTests {

    @Nested
    class Similarity {

        private final SimilarityIndex<String> index = SimilarityIndex.build(List.of(
                "VPN disconnects every few minutes from home",
                "Printer jams on every print job",
                "Payment page times out at checkout",
                "VPN client closes after the update"), text -> text);

        @Test
        void theMostSimilarTextComesFirstWithTheWordsItShares() {
            List<SimilarityIndex.Match<String>> matches = index.search("vpn keeps disconnecting at home", 3, 0.0);

            assertThat(matches.get(0).item()).isEqualTo("VPN disconnects every few minutes from home");
            assertThat(matches.get(0).sharedWords()).contains("vpn", "home");
            assertThat(matches).extracting(SimilarityIndex.Match::item).doesNotContain("Printer jams on every print job");
        }

        @Test
        void aTextIsFullySimilarToItself() {
            assertThat(index.search("Printer jams on every print job", 1, 0.0).get(0).score()).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void aRareWordCountsMoreThanACommonOne() {
            // "vpn" is in two texts, "checkout" in one: a query with both prefers the checkout text.
            assertThat(index.search("vpn checkout", 1, 0.0).get(0).item()).isEqualTo("Payment page times out at checkout");
        }

        @Test
        void noWordInCommonMeansNoMatchAndTheMinimumScoreFilters() {
            assertThat(index.search("coffee machine", 5, 0.0)).isEmpty();
            assertThat(index.search("", 5, 0.0)).isEmpty();
            assertThat(index.search("vpn printer payment", 5, 0.9)).isEmpty();
        }
    }

    @Nested
    class NaiveBayes {

        private final NaiveBayesClassifier<String> classifier = new NaiveBayesClassifier<>(List.of(
                new Example<>("vpn drops when working from home", "Network"),
                new Example<>("wifi slow in the meeting room", "Network"),
                new Example<>("router restarts and the internet is gone", "Network"),
                new Example<>("printer jams and prints blank pages", "Hardware"),
                new Example<>("laptop battery will not charge", "Hardware"),
                new Example<>("monitor stays black when docked", "Hardware")));

        @Test
        void itLabelsANewTextWithTheLabelItsWordsBelongTo() {
            List<NaiveBayesClassifier.Prediction<String>> predictions = classifier.predict("the wifi router keeps dropping");

            assertThat(predictions.get(0).label()).isEqualTo("Network");
            assertThat(predictions.get(0).probability()).isGreaterThan(0.8);
            assertThat(predictions.get(0).evidence()).contains("wifi", "router");
        }

        @Test
        void theProbabilitiesAddUpToOne() {
            double sum = classifier.predict("laptop wifi").stream()
                    .mapToDouble(NaiveBayesClassifier.Prediction::probability).sum();

            assertThat(sum).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void withNoKnownWordItDoesNotGuess() {
            assertThat(classifier.predict("the coffee machine is empty")).isEmpty();
        }

        @Test
        void aWeightedExampleCountsMoreThanAnOrdinaryOne() {
            NaiveBayesClassifier<String> weighted = new NaiveBayesClassifier<>(List.of(
                    new Example<>("screen", "Hardware", 5.0),
                    new Example<>("screen sharing", "Software")));

            assertThat(weighted.predict("screen").get(0).label()).isEqualTo("Hardware");
            assertThat(weighted.exampleCount()).isEqualTo(6.0);
        }
    }

    @Nested
    class Triage {

        private final TriageAdvisor advisor = new TriageAdvisor();

        @Test
        void manyPeopleWhoCannotWorkIsHighAndHigh() {
            TriageAdvice advice = advisor.advise("All users in finance cannot work since nine");

            assertThat(advice.impact()).isEqualTo(Impact.HIGH);
            assertThat(advice.impactReason()).isEqualTo("all users");
            assertThat(advice.urgency()).isEqualTo(Urgency.HIGH);
            assertThat(advice.urgencyReason()).isEqualTo("cannot work");
        }

        @Test
        void oneDeviceThatCanWaitIsLowAndLow() {
            TriageAdvice advice = advisor.advise("My laptop fan is noisy, not urgent");

            assertThat(advice.impact()).isEqualTo(Impact.LOW);
            assertThat(advice.urgency()).isEqualTo(Urgency.LOW);   // "not urgent" is read before "urgent"
        }

        @Test
        void aTeamWithASlowServiceIsMediumAndMedium() {
            TriageAdvice advice = advisor.advise("The shared drive is slow for our team");

            assertThat(advice.impact()).isEqualTo(Impact.MEDIUM);
            assertThat(advice.urgency()).isEqualTo(Urgency.MEDIUM);
        }

        @Test
        void withoutAClueThereIsNoAdviceRatherThanAGuess() {
            assertThat(advisor.advise("Question about the new screens").isEmpty()).isTrue();
            assertThat(advisor.advise("   ").isEmpty()).isTrue();
            assertThat(advisor.advise("Customers see an error on the invoice page").impact()).isEqualTo(Impact.HIGH);
        }
    }
}
