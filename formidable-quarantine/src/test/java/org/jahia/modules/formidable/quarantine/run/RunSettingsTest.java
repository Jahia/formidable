package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.Configs;
import org.jahia.modules.formidable.quarantine.state.Backoff;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The configuration of the run reaches its back-off, and a back-off it cannot build falls back to the defaults. */
class RunSettingsTest {

    @Test
    void theFactorOfTheConfigurationReachesTheBackoff() {
        RunSettings settings = RunSettings.of(Configs.of(RunConfig.class,
                Map.of("backoffInitialSeconds", 10L, "backoffFactor", 3.0, "backoffCapSeconds", 600L)));

        assertEquals(Duration.ofSeconds(10), settings.backoff().delay(1));
        assertEquals(Duration.ofSeconds(30), settings.backoff().delay(2));
        assertEquals(Duration.ofSeconds(90), settings.backoff().delay(3));
    }

    @Test
    void theDefaultsOfTheConfigurationAreTheDefaultBackoff() {
        RunSettings settings = RunSettings.of(Configs.of(RunConfig.class, Map.of()));

        assertEquals(Backoff.DEFAULT.factor(), settings.backoff().factor());
        assertEquals(Backoff.DEFAULT.delay(3), settings.backoff().delay(3));
        assertEquals(Backoff.DEFAULT.maxAttempts(), settings.backoff().maxAttempts());
    }

    @Test
    void aFactorBelowOneFallsBackToTheDefaultBackoff() {
        RunSettings settings = RunSettings.of(Configs.of(RunConfig.class, Map.of("backoffFactor", 0.5)));

        assertSame(Backoff.DEFAULT, settings.backoff());
    }
}
