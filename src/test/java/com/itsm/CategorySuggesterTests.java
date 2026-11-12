package com.itsm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.itsm.domain.Category;
import com.itsm.intelligence.CategorySuggester;
import com.itsm.intelligence.CategorySuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Suggesting a category from keywords, on plain objects. The categories and keywords are those of the demo data. */
class CategorySuggesterTests {

    private final CategorySuggester suggester = new CategorySuggester();

    private final List<Category> categories = List.of(
            new Category("Hardware", List.of("laptop", "desktop", "printer", "monitor", "keyboard", "mouse", "battery", "device")),
            new Category("Software", List.of("software", "install", "installation", "update", "license", "crash", "version")),
            new Category("Network", List.of("network", "wifi", "vpn", "internet", "connectivity", "dns", "router", "lan")),
            new Category("Database", List.of("database", "sql", "query", "table", "deadlock", "replication")),
            new Category("Cloud", List.of("cloud", "virtual machine", "container", "kubernetes", "deployment", "region")),
            new Category("Security", List.of("security", "virus", "malware", "phishing", "breach", "ransomware", "firewall")),
            new Category("Authentication", List.of("login", "password", "sso", "otp", "locked", "credentials", "sign in")),
            new Category("Application", List.of("application", "app", "portal", "payment", "transaction", "page", "unavailable")),
            new Category("Storage", List.of("storage", "disk", "drive", "backup", "quota", "space", "file share")));

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({
            "Payment application unavailable,                    Application",
            "Payment transaction failing,                        Application",
            "Unable to make payment,                             Application",
            "Cannot connect to the VPN from home,                Network",
            "My laptop battery dies after ten minutes,           Hardware",
            "Forgot my password and now my account is locked,    Authentication",
            "I got a phishing email with a virus attached,       Security",
            "The SQL query on the orders table hits a deadlock,  Database",
            "Shared drive is out of disk space,                  Storage",
            "The virtual machine in the cloud will not start,    Cloud",
            "Excel crashes after the latest update,              Software"})
    void theCategoryIsSuggestedFromTheWordsOfTheReport(String text, String expectedCategory) {
        assertThat(suggester.suggest(text, categories))
                .map(suggestion -> suggestion.category().getName())
                .contains(expectedCategory);
    }

    @Test
    void theSuggestionExplainsItselfWithTheKeywordsItFound() {
        CategorySuggestion suggestion = suggester.suggest(
                "Cannot connect. The VPN drops and the wifi is slow.", categories).orElseThrow();

        assertThat(suggestion.category().getName()).isEqualTo("Network");
        assertThat(suggestion.matchedKeywords()).containsExactly("vpn", "wifi");
        assertThat(suggestion.explain())
                .isEqualTo("Category Network was chosen automatically because the report mentions: vpn, wifi.");
    }

    @Test
    void theCategoryWithTheMostKeywordsWins() {
        // One Hardware keyword (laptop), two Network keywords (wifi, router).
        assertThat(suggester.suggest("My laptop loses wifi near the router", categories))
                .map(suggestion -> suggestion.category().getName()).contains("Network");
    }

    @Test
    void whenTwoCategoriesAreLevelTheMoreSpecificKeywordWins() {
        // "app" (Application) against "deployment" (Cloud): one keyword each, and "deployment" is the longer hint.
        assertThat(suggester.suggest("The app deployment is stuck", categories))
                .map(suggestion -> suggestion.category().getName()).contains("Cloud");
    }

    @Test
    void keywordsMatchWholeWordsOnlyAndInAnyLetterCase() {
        // "happy" contains "app", and "tablet" contains "table": neither may count.
        assertThat(suggester.suggest("I would be happy to get a new tablet", categories)).isEmpty();
        assertThat(suggester.suggest("VPN", categories))
                .map(suggestion -> suggestion.category().getName()).contains("Network");
    }

    @Test
    void aKeywordOfSeveralWordsIsMatchedAsAPhrase() {
        CategorySuggestion suggestion = suggester.suggest("I cannot sign in this morning", categories).orElseThrow();

        assertThat(suggestion.category().getName()).isEqualTo("Authentication");
        assertThat(suggestion.matchedKeywords()).containsExactly("sign in");
    }

    @Test
    void withNoKeywordFoundThereIsNoSuggestion() {
        assertThat(suggester.suggest("Something is odd and I do not know what", categories)).isEmpty();
        assertThat(suggester.suggest("", categories)).isEmpty();
        assertThat(suggester.suggest(null, categories)).isEmpty();
        assertThat(suggester.suggest("vpn", List.of())).isEmpty();
    }
}
