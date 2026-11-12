package com.itsm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The application starts: every bean can be created and the database schema can be built. */
@SpringBootTest
@ActiveProfiles("test")
class ItsmApplicationTests {

    @Test
    void applicationStarts() {
        // Nothing to do here: the test fails if the application context cannot start.
    }
}
