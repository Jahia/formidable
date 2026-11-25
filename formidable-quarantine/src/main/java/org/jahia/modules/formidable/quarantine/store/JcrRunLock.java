package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.settings.SettingsBean;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.RepositoryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The {@link RunLock} of the module: a lock node {@code runLock} of the type {@code fmdbq:runLock} under the root of
 * the quarantine tree of a site, in live, written with a system session. It stores the server that holds the lock and
 * the end of its lease. Two owners that take the lock at the same moment both write the node; the repository saves
 * the first write and refuses the second as stale, so the second owner does not get the lock.
 *
 * <p>The owner of a lease is the server, named by {@code cluster.node.serverId} of its settings ({@value #STANDALONE}
 * when it has none), a token drawn for each activation of this component (Declarative Services creates a new
 * instance at each activation), and a token drawn for each {@link #take}: each holder, such as one pass of the run,
 * is an owner of its own. Another holder, of this activation or of a new one on the same server, waits for the lease
 * unless its holder released it. At deactivation, the component releases every lease it granted and grants none any
 * more: a lease of the old activation is no longer {@link Lease#valid() valid}, so a pass still under way stops.</p>
 */
@Component(service = RunLock.class, immediate = true)
public class JcrRunLock implements RunLock {

    private static final Logger log = LoggerFactory.getLogger(JcrRunLock.class);

    /** The name of the lock node under the root of the tree. */
    public static final String LOCK_NAME = "runLock";
    public static final String LOCK_TYPE = "fmdbq:runLock";
    static final String OWNER = "owner";
    static final String UNTIL = "until";
    private static final String SERVER_ID = "cluster.node.serverId";
    private static final String STANDALONE = "standalone";

    private final Clock clock;
    private final String activation;
    /** The leases this activation granted and that were not released. */
    private final Set<JcrLease> leases = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean deactivated = new AtomicBoolean();

    public JcrRunLock() {
        this(Clock.systemUTC(), SettingsBean.getInstance().getString(SERVER_ID, STANDALONE) + ":" + UUID.randomUUID());
    }

    /** @param activation the server and the token of this activation */
    JcrRunLock(Clock clock, String activation) {
        this.clock = clock;
        this.activation = activation;
    }

    /** @return the server and the token of this activation, the start of the owner of each of its leases */
    String activation() {
        return activation;
    }

    /** Releases every lease of this activation, and refuses every lock from now on. */
    @Deactivate
    public void deactivate() {
        deactivated.set(true);
        for (JcrLease lease : Set.copyOf(leases)) {
            lease.release();
        }
    }

    /** Runs the callback in a system session of the live workspace. A unit test overrides it with a mocked session. */
    <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, JcrQuarantineStore.LIVE_WORKSPACE, null,
                callback);
    }

    @Override
    public Optional<Lease> take(String siteKey, Duration length) throws QuarantineStoreException {
        if (deactivated.get()) {
            log.info("The lock of the run of site {} is refused: the module stopped", siteKey);
            return Optional.empty();
        }
        JcrLease lease = new JcrLease(siteKey, activation + ":" + UUID.randomUUID());
        if (!lease.write(length)) {
            return Optional.empty();
        }
        leases.add(lease);
        return Optional.of(lease);
    }

    @Override
    public boolean isHeld(String siteKey) throws QuarantineStoreException {
        try {
            return inLiveSystemSession(session -> {
                String lockPath = lockPath(siteKey);
                if (!session.nodeExists(lockPath)) {
                    return false;
                }
                RunLease current = leaseOf(session.getNode(lockPath));
                return current.owner() != null && current.until() != null && current.until().isAfter(clock.instant());
            });
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("The lock of the run of site " + siteKey + " cannot be read", e);
        }
    }

    private static String lockPath(String siteKey) {
        return QuarantineTree.rootPath(siteKey) + "/" + LOCK_NAME;
    }

    /** The lease of one holder, written on the lock node of its site. */
    private final class JcrLease implements Lease {

        private final String siteKey;
        private final String owner;
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicReference<Instant> until = new AtomicReference<>(Instant.MIN);

        JcrLease(String siteKey, String owner) {
            this.siteKey = siteKey;
            this.owner = owner;
        }

        @Override
        public String siteKey() {
            return siteKey;
        }

        @Override
        public boolean valid() {
            return !released.get() && !deactivated.get() && clock.instant().isBefore(until.get());
        }

        @Override
        public boolean extend(Duration length) throws QuarantineStoreException {
            return valid() && write(length);
        }

        /** Writes this owner and the end of its lease, when the rule of the lease allows it. */
        boolean write(Duration length) throws QuarantineStoreException {
            Instant end = clock.instant().plus(length);
            try {
                boolean written = inLiveSystemSession(session -> write(session, end));
                if (written) {
                    until.set(end);
                }
                return written;
            } catch (InvalidItemStateException | ItemExistsException e) {
                log.info("The lock of the run of site {} was taken by another owner at the same moment", siteKey);
                return false;
            } catch (RepositoryException e) {
                throw new QuarantineStoreException("The lock of the run of site " + siteKey + " cannot be taken", e);
            }
        }

        private boolean write(JCRSessionWrapper session, Instant end) throws RepositoryException {
            String rootPath = QuarantineTree.rootPath(siteKey);
            if (!session.nodeExists(rootPath)) {
                return true;
            }
            JCRNodeWrapper root = session.getNode(rootPath);
            JCRNodeWrapper lock = root.hasNode(LOCK_NAME) ? root.getNode(LOCK_NAME) : null;
            if (!RunLease.mayTake(lock == null ? null : leaseOf(lock), owner, clock.instant())) {
                return false;
            }
            if (lock == null) {
                lock = root.addNode(LOCK_NAME, LOCK_TYPE);
            }
            lock.setProperty(OWNER, owner);
            lock.setProperty(UNTIL, calendar(end));
            session.save();
            return true;
        }

        @Override
        public void release() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            leases.remove(this);
            try {
                inLiveSystemSession(session -> {
                    String lockPath = lockPath(siteKey);
                    if (session.nodeExists(lockPath)) {
                        JCRNodeWrapper lock = session.getNode(lockPath);
                        if (owner.equals(leaseOf(lock).owner())) {
                            lock.setProperty(UNTIL, calendar(clock.instant()));
                            session.save();
                        }
                    }
                    return null;
                });
            } catch (RepositoryException | RuntimeException e) {
                log.warn("The lock of the run of site {} could not be released, its lease ends it: {}", siteKey,
                        e.toString());
            }
        }
    }

    private static RunLease leaseOf(JCRNodeWrapper lock) throws RepositoryException {
        String owner = lock.hasProperty(OWNER) ? lock.getProperty(OWNER).getString() : null;
        Instant until = lock.hasProperty(UNTIL) ? lock.getProperty(UNTIL).getDate().toInstant() : null;
        return new RunLease(owner, until);
    }

    private static Calendar calendar(Instant at) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(at.toEpochMilli());
        return calendar;
    }
}
