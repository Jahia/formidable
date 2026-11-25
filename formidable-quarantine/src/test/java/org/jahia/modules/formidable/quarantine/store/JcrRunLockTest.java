package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jcr.InvalidItemStateException;
import javax.jcr.RepositoryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private static final String LOCK_PATH = ROOT + "/" + JcrRunLock.LOCK_NAME;
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(10);
    /** This activation of the lock service on server A. */
    private static final String ACTIVATION = "serverA:act1";

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
        when(root.addNode(JcrRunLock.LOCK_NAME, JcrRunLock.LOCK_TYPE)).thenReturn(lockNode);
        lock = new JcrRunLock(Clock.fixed(NOW, ZoneOffset.UTC), ACTIVATION) {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                return callback.doInJCR(session);
            }
        };
    }

    /** The lock node exists, with this owner and this end of its lease. */
    private void existingLease(String owner, Instant until) throws RepositoryException {
        when(root.hasNode(JcrRunLock.LOCK_NAME)).thenReturn(true);
        when(root.getNode(JcrRunLock.LOCK_NAME)).thenReturn(lockNode);
        when(session.nodeExists(LOCK_PATH)).thenReturn(true);
        when(session.getNode(LOCK_PATH)).thenReturn(lockNode);
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

    /** Takes the lock on a site with no lock node yet, and answers the owner it wrote. */
    private String takeFresh() throws Exception {
        when(root.hasNode(JcrRunLock.LOCK_NAME)).thenReturn(false);
        assertTrue(lock.take(SITE, LEASE).isPresent());
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(lockNode).setProperty(eq(JcrRunLock.OWNER), owner.capture());
        return owner.getValue();
    }

    /** The ends of lease written on the lock node, in order. */
    private List<Instant> writtenEnds(int count) throws RepositoryException {
        ArgumentCaptor<Calendar> until = ArgumentCaptor.forClass(Calendar.class);
        verify(lockNode, times(count)).setProperty(eq(JcrRunLock.UNTIL), until.capture());
        return until.getAllValues().stream().map(Calendar::toInstant).toList();
    }

    @Test
    void aSiteWithNoTreeHasNothingToLock() throws Exception {
        when(session.nodeExists(ROOT)).thenReturn(false);

        assertTrue(lock.take(SITE, LEASE).isPresent());

        verify(session, never()).save();
    }

    @Test
    void theFirstTakeCreatesTheLockNodeWithItsOwnerAndTheEndOfTheLease() throws Exception {
        String owner = takeFresh();

        assertTrue(owner.startsWith(ACTIVATION + ":"), owner);
        assertEquals(List.of(NOW.plus(LEASE)), writtenEnds(1));
        verify(session).save();
    }

    @Test
    void eachTakeIsAnOwnerOfItsOwnSoASecondHolderWaitsForTheLease() throws Exception {
        String first = takeFresh();
        existingLease(first, NOW.plus(LEASE));

        assertFalse(lock.take(SITE, LEASE).isPresent());

        verify(session, times(1)).save();
    }

    @Test
    void aLeaseOfAnotherServerThatHasNotEndedRefusesTheLockAndWritesNothing() throws Exception {
        existingLease("serverB:act1:pass1", NOW.plusSeconds(60));

        assertFalse(lock.take(SITE, LEASE).isPresent());

        verify(lockNode, never()).setProperty(anyString(), anyString());
        verify(session, never()).save();
    }

    @Test
    void anotherActivationOnTheSameServerWaitsForTheLeaseOfTheOneBefore() throws Exception {
        existingLease("serverA:act0:pass1", NOW.plusSeconds(60));

        assertFalse(lock.take(SITE, LEASE).isPresent());

        verify(session, never()).save();
    }

    @Test
    void anEndedLeaseIsTakenOver() throws Exception {
        // a release ends the lease at the time of the release
        existingLease("serverA:act0:pass1", NOW);

        Optional<RunLock.Lease> taken = lock.take(SITE, LEASE);

        assertTrue(taken.isPresent());
        assertEquals(List.of(NOW.plus(LEASE)), writtenEnds(1));
        verify(session).save();
    }

    @Test
    void aSaveThatAnotherOwnerMadeStaleRefusesTheLock() throws Exception {
        existingLease("serverB:act1:pass1", NOW.minusSeconds(1));
        doThrow(new InvalidItemStateException("stale")).when(session).save();

        assertFalse(lock.take(SITE, LEASE).isPresent());
    }

    @Test
    void aRepositoryFailureIsAnErrorOfTheStore() throws Exception {
        when(session.getNode(ROOT)).thenThrow(new RepositoryException("down"));

        assertThrows(QuarantineStoreException.class, () -> lock.take(SITE, LEASE));
    }

    @Test
    void theReleaseEndsTheLeaseNowWhenItsHolderStillHoldsIt() throws Exception {
        String owner = takeFresh();
        existingLease(owner, NOW.plus(LEASE));

        lock.deactivate();

        assertEquals(List.of(NOW.plus(LEASE), NOW), writtenEnds(2));
        verify(session, times(2)).save();
    }

    @Test
    void theReleaseWritesNothingWhenAnotherOwnerTookTheLock() throws Exception {
        takeFresh();
        existingLease("serverB:act1:pass1", NOW.plus(LEASE));

        lock.deactivate();

        verify(session, times(1)).save();
    }

    @Test
    void theHolderExtendsItsLeaseAndCannotOnceReleased() throws Exception {
        when(root.hasNode(JcrRunLock.LOCK_NAME)).thenReturn(false);
        RunLock.Lease lease = lock.take(SITE, LEASE).orElseThrow();
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(lockNode).setProperty(eq(JcrRunLock.OWNER), owner.capture());
        existingLease(owner.getValue(), NOW.plus(LEASE));

        assertTrue(lease.valid());
        assertTrue(lease.extend(Duration.ofMinutes(20)));
        lease.release();

        assertFalse(lease.valid());
        assertFalse(lease.extend(LEASE));
        assertEquals(List.of(NOW.plus(LEASE), NOW.plus(Duration.ofMinutes(20)), NOW), writtenEnds(3));
    }

    @Test
    void theDeactivationEndsEveryLeaseAndRefusesAnyOther() throws Exception {
        when(root.hasNode(JcrRunLock.LOCK_NAME)).thenReturn(false);
        RunLock.Lease lease = lock.take(SITE, LEASE).orElseThrow();
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(lockNode).setProperty(eq(JcrRunLock.OWNER), owner.capture());
        existingLease(owner.getValue(), NOW.plus(LEASE));

        lock.deactivate();

        assertFalse(lease.valid());
        assertFalse(lock.take(SITE, LEASE).isPresent());
        // one write at the take, one at the release; none after the deactivation
        verify(session, times(2)).save();
    }

    @Test
    void aLockIsHeldWhileALeaseOfAnyOwnerHasNotEnded() throws Exception {
        assertFalse(lock.isHeld(SITE));

        existingLease("serverB:act1:pass1", NOW.plusSeconds(1));
        assertTrue(lock.isHeld(SITE));

        existingLease("serverB:act1:pass1", NOW);
        assertFalse(lock.isHeld(SITE));
        verify(session, never()).save();
    }

}
