package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.InvalidItemStateException;
import javax.jcr.RepositoryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The lock node of the run of a site, written with a mocked system session. */
class JcrRunLockTest {

    private static final String SITE = "siteA";
    private static final String ROOT = "/sites/siteA/formidable-quarantine";
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    /** This activation of the lock service on server A. */
    private static final String OWNER = "serverA:new";

    private JCRSessionWrapper session;
    private JCRNodeWrapper root;
    private JCRNodeWrapper lockNode;
    private JcrRunLock lock;

    @BeforeEach
    void setUp() throws RepositoryException {
        session = mock(JCRSessionWrapper.class);
        root = mock(JCRNodeWrapper.class);
        lockNode = mock(JCRNodeWrapper.class);
        when(session.nodeExists(ROOT)).thenReturn(true);
        when(session.getNode(ROOT)).thenReturn(root);
        lock = new JcrRunLock(Clock.fixed(NOW, ZoneOffset.UTC), OWNER) {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                return callback.doInJCR(session);
            }
        };
    }

    private void existingLease(String owner, Instant until) throws RepositoryException {
        when(root.hasNode(JcrRunLock.LOCK_NAME)).thenReturn(true);
        when(root.getNode(JcrRunLock.LOCK_NAME)).thenReturn(lockNode);
        when(lockNode.hasProperty(JcrRunLock.OWNER)).thenReturn(true);
        when(lockNode.hasProperty(JcrRunLock.UNTIL)).thenReturn(true);
        JCRPropertyWrapper ownerProperty = mock(JCRPropertyWrapper.class);
        when(ownerProperty.getString()).thenReturn(owner);
        JCRPropertyWrapper untilProperty = mock(JCRPropertyWrapper.class);
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(until.toEpochMilli());
        when(untilProperty.getDate()).thenReturn(calendar);
        when(lockNode.getProperty(JcrRunLock.OWNER)).thenReturn(ownerProperty);
        when(lockNode.getProperty(JcrRunLock.UNTIL)).thenReturn(untilProperty);
    }

    @Test
    void aSiteWithNoTreeHasNothingToLock() throws Exception {
        when(session.nodeExists(ROOT)).thenReturn(false);

        assertTrue(lock.take(SITE, Duration.ofMinutes(10)));

        verify(session, never()).save();
    }

    @Test
    void theFirstTakeCreatesTheLockNodeWithTheServerAndTheEndOfTheLease() throws Exception {
        when(root.hasNode(JcrRunLock.LOCK_NAME)).thenReturn(false);
        when(root.addNode(JcrRunLock.LOCK_NAME, JcrRunLock.LOCK_TYPE)).thenReturn(lockNode);

        assertTrue(lock.take(SITE, Duration.ofMinutes(10)));

        verify(lockNode).setProperty(JcrRunLock.OWNER, OWNER);
        verify(lockNode).setProperty(eq(JcrRunLock.UNTIL), any(Calendar.class));
        verify(session).save();
    }

    @Test
    void aLeaseOfAnotherServerThatHasNotEndedRefusesTheLockAndWritesNothing() throws Exception {
        existingLease("serverB:t1", NOW.plusSeconds(60));

        assertFalse(lock.take(SITE, Duration.ofMinutes(10)));

        verify(lockNode, never()).setProperty(anyString(), anyString());
        verify(session, never()).save();
    }

    @Test
    void anEndedLeaseOfAnotherServerIsTakenOver() throws Exception {
        existingLease("serverB:t1", NOW.minusSeconds(1));

        assertTrue(lock.take(SITE, Duration.ofMinutes(10)));

        verify(lockNode).setProperty(JcrRunLock.OWNER, OWNER);
        verify(session).save();
    }

    @Test
    void aSaveThatAnotherServerMadeStaleRefusesTheLock() throws Exception {
        existingLease("serverB:t1", NOW.minusSeconds(1));
        doThrow(new InvalidItemStateException("stale")).when(session).save();

        assertFalse(lock.take(SITE, Duration.ofMinutes(10)));
    }

    @Test
    void aRepositoryFailureIsAnErrorOfTheStore() throws Exception {
        when(session.getNode(ROOT)).thenThrow(new RepositoryException("down"));

        assertThrows(QuarantineStoreException.class, () -> lock.take(SITE, Duration.ofMinutes(10)));
    }

    @Test
    void theReleaseEndsTheLeaseOfThisServerOnly() throws Exception {
        String lockPath = ROOT + "/" + JcrRunLock.LOCK_NAME;
        when(session.nodeExists(lockPath)).thenReturn(true);
        when(session.getNode(lockPath)).thenReturn(lockNode);
        existingLease("serverB:t1", NOW.plusSeconds(60));

        lock.release(SITE);
        verify(session, never()).save();

        existingLease("serverA:new", NOW.plusSeconds(60));
        lock.release(SITE);
        verify(lockNode).setProperty(eq(JcrRunLock.UNTIL), any(Calendar.class));
        verify(session).save();
    }

    @Test
    void anotherActivationOnTheSameServerWaitsForTheLeaseOfTheOneBefore() throws Exception {
        existingLease("serverA:old", NOW.plusSeconds(60));

        assertFalse(lock.take(SITE, Duration.ofMinutes(10)));

        verify(session, never()).save();
    }

    @Test
    void anotherActivationOnTheSameServerTakesTheLockThatTheOneBeforeReleased() throws Exception {
        // a release ends the lease at the time of the release
        existingLease("serverA:old", NOW);

        assertTrue(lock.take(SITE, Duration.ofMinutes(10)));

        verify(lockNode).setProperty(JcrRunLock.OWNER, OWNER);
    }

    @Test
    void theDeactivationReleasesTheLocksItHoldsAndRefusesAnyOther() throws Exception {
        when(root.hasNode(JcrRunLock.LOCK_NAME)).thenReturn(false);
        when(root.addNode(JcrRunLock.LOCK_NAME, JcrRunLock.LOCK_TYPE)).thenReturn(lockNode);
        assertTrue(lock.take(SITE, Duration.ofMinutes(10)));
        String lockPath = ROOT + "/" + JcrRunLock.LOCK_NAME;
        when(session.nodeExists(lockPath)).thenReturn(true);
        when(session.getNode(lockPath)).thenReturn(lockNode);
        existingLease(OWNER, NOW.plusSeconds(600));

        lock.deactivate();

        // one write of the end of the lease at the take, one at the release
        verify(lockNode, times(2)).setProperty(eq(JcrRunLock.UNTIL), any(Calendar.class));
        verify(session, times(2)).save();
        assertFalse(lock.take(SITE, Duration.ofMinutes(10)));
        verify(session, times(2)).save();
    }

    @Test
    void aSiteReleasedBeforeTheDeactivationIsNotReleasedAgain() throws Exception {
        when(session.nodeExists(ROOT)).thenReturn(false);
        assertTrue(lock.take(SITE, Duration.ofMinutes(10)));
        lock.release(SITE);
        String lockPath = ROOT + "/" + JcrRunLock.LOCK_NAME;
        when(session.nodeExists(lockPath)).thenReturn(true);
        when(session.getNode(lockPath)).thenReturn(lockNode);
        existingLease(OWNER, NOW.plusSeconds(600));

        lock.deactivate();

        verify(session, never()).save();
    }
}
