package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The live pass of a migration must be invisible to JCR observation — Jahia's UGCListener
 * would otherwise mark the rewritten properties as live-owned and every later publication
 * would skip them (#281) — and must flush the output caches itself when it rewrote
 * anything, since the cache invalidation listener does not see the change either. A pass
 * that fails has possibly saved nodes already: it flushes too.
 */
class MigrationSessionsTest {

    private final JCRSessionWrapper session = mock(JCRSessionWrapper.class);
    private final JCRTemplate template = mock(JCRTemplate.class);
    private final AtomicInteger flushes = new AtomicInteger();

    @BeforeEach
    void runCallbacksOnTheMockSession() throws RepositoryException {
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.<JCRCallback<Integer>>getArgument(3).doInJCR(session));
    }

    @AfterEach
    void leaveObservationOn() {
        JCRObservationManager.setAllEventListenersDisabled(Boolean.FALSE);
    }

    private int execute(String workspace, JCRCallback<Integer> pass) throws RepositoryException {
        return MigrationSessions.execute(template, flushes::incrementAndGet, workspace, pass);
    }

    @Test
    void theLivePassRunsWithObservationOffAndFlushesTheOutputCachesWhenItRewrote() throws Exception {
        AtomicReference<Boolean> observationDisabledDuringPass = new AtomicReference<>();

        int rewritten = execute("live", s -> {
            observationDisabledDuringPass.set(observationDisabled());
            return 2;
        });

        assertEquals(2, rewritten);
        assertTrue(observationDisabledDuringPass.get(), "observation must be off while the live pass writes");
        assertFalse(observationDisabled(), "observation must be back on after the pass");
        assertEquals(1, flushes.get(), "a live rewrite must flush the output caches");
    }

    @Test
    void aLivePassThatRewroteNothingLeavesTheOutputCachesAlone() throws Exception {
        assertEquals(0, execute("live", s -> 0));
        assertEquals(0, execute("live", s -> null));

        assertEquals(0, flushes.get());
    }

    @Test
    void theDefaultPassKeepsObservationOnAndNeverFlushes() throws Exception {
        AtomicReference<Boolean> observationDisabledDuringPass = new AtomicReference<>();

        int rewritten = execute("default", s -> {
            observationDisabledDuringPass.set(observationDisabled());
            return 5;
        });

        assertEquals(5, rewritten);
        assertFalse(observationDisabledDuringPass.get(), "the default pass is an ordinary system edit");
        assertEquals(0, flushes.get());
    }

    @Test
    void observationComesBackOnAndTheCachesAreFlushedWhenTheLivePassFails() {
        assertThrows(RepositoryException.class, () -> execute("live", s -> {
            throw new RepositoryException("boom");
        }));

        assertFalse(observationDisabled(), "a failing pass must not leave the thread deaf to events");
        assertEquals(1, flushes.get(), "nodes saved before the failure are live: flush rather than guess");
    }

    @Test
    void anUnexpectedFailureOfTheLivePassFlushesTooAndSurfacesAsItself() {
        assertThrows(NullPointerException.class, () -> execute("live", s -> {
            throw new NullPointerException("out of migrateNode");
        }));

        assertFalse(observationDisabled());
        assertEquals(1, flushes.get());
    }

    @Test
    void aFailingFlushNeverMasksTheFailureOfThePass() {
        Runnable failingFlush = () -> {
            throw new IllegalStateException("cache manager gone");
        };

        RepositoryException failure = assertThrows(RepositoryException.class,
                () -> MigrationSessions.execute(template, failingFlush, "live", s -> {
                    throw new RepositoryException("boom");
                }));

        assertEquals("boom", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void theDefaultPassFailureIsNotFlushed() {
        assertThrows(RepositoryException.class, () -> execute("default", s -> {
            throw new RepositoryException("boom");
        }));

        assertEquals(0, flushes.get());
    }

    /** The flag has no getter: read the core's thread-local the way {@code consume} does. */
    @SuppressWarnings("unchecked")
    private static boolean observationDisabled() {
        try {
            Field field = JCRObservationManager.class.getDeclaredField("allEventListenersDisabled");
            field.setAccessible(true);
            return Boolean.TRUE.equals(((ThreadLocal<Boolean>) field.get(null)).get());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
