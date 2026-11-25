package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.state.Backoff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * The settings of the run, as read from {@link RunConfig}.
 *
 * @param allowInProcessFirst true to lift the refusal of an in-process check placed first
 * @param backoff             when the run tries a submission again, and after how many failures it gives up
 */
public record RunSettings(boolean allowInProcessFirst, Backoff backoff) {

    private static final Logger log = LoggerFactory.getLogger(RunSettings.class);

    /** The defaults: the in-process refusal on, {@link Backoff#DEFAULT}. */
    public static final RunSettings DEFAULT = new RunSettings(false, Backoff.DEFAULT);

    public RunSettings {
        Objects.requireNonNull(backoff, "backoff");
    }

    /**
     * @return the settings of the configuration; a back-off the configuration cannot build, such as a cap below the
     *         initial delay, falls back to {@link Backoff#DEFAULT} with a warning
     */
    public static RunSettings of(RunConfig config) {
        Backoff backoff;
        try {
            backoff = new Backoff(Duration.ofSeconds(config.backoffInitialSeconds()),
                    Duration.ofSeconds(config.backoffCapSeconds()), config.maxAttempts());
        } catch (IllegalArgumentException e) {
            log.warn("The back-off of the run is not valid ({}): the defaults apply", e.getMessage());
            backoff = Backoff.DEFAULT;
        }
        return new RunSettings(config.allowInProcessFirst(), backoff);
    }
}
