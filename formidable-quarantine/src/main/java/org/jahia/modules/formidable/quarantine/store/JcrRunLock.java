package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.settings.SettingsBean;
import org.osgi.service.component.annotations.Component;
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
import java.util.TimeZone;

/**
 * The {@link RunLock} of the module: a lock node {@code runLock} of the type {@code fmdbq:runLock} under the root of
 * the quarantine tree of a site, in live, written with a system session. It stores the server that holds the lock and
 * the end of its lease. Two servers that take the lock at the same moment both write the node; the repository saves
 * the first write and refuses the second as stale, so the second server does not get the lock.
 *
 * <p>A server is named by {@code cluster.node.serverId} of its settings: a standalone server holds the lock under
 * one name.</p>
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
    private final String server;

    public JcrRunLock() {
        this(Clock.systemUTC(), SettingsBean.getInstance().getString(SERVER_ID, STANDALONE));
    }

    JcrRunLock(Clock clock, String server) {
        this.clock = clock;
        this.server = server;
    }

    /** Runs the callback in a system session of the live workspace. A unit test overrides it with a mocked session. */
    <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, JcrQuarantineStore.LIVE_WORKSPACE, null,
                callback);
    }

    @Override
    public boolean take(String siteKey, Duration lease) throws QuarantineStoreException {
        try {
            return inLiveSystemSession(session -> take(session, siteKey, lease));
        } catch (InvalidItemStateException | ItemExistsException e) {
            log.info("The lock of the run of site {} was taken by another server at the same moment", siteKey);
            return false;
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("The lock of the run of site " + siteKey + " cannot be taken", e);
        }
    }

    private boolean take(JCRSessionWrapper session, String siteKey, Duration lease) throws RepositoryException {
        String rootPath = QuarantineTree.rootPath(siteKey);
        if (!session.nodeExists(rootPath)) {
            return true;
        }
        JCRNodeWrapper root = session.getNode(rootPath);
        Instant now = clock.instant();
        JCRNodeWrapper lock = root.hasNode(LOCK_NAME) ? root.getNode(LOCK_NAME) : null;
        if (!RunLease.mayTake(lock == null ? null : leaseOf(lock), server, now)) {
            return false;
        }
        if (lock == null) {
            lock = root.addNode(LOCK_NAME, LOCK_TYPE);
        }
        lock.setProperty(OWNER, server);
        lock.setProperty(UNTIL, calendar(now.plus(lease)));
        session.save();
        return true;
    }

    @Override
    public void release(String siteKey) {
        try {
            inLiveSystemSession(session -> {
                String lockPath = QuarantineTree.rootPath(siteKey) + "/" + LOCK_NAME;
                if (session.nodeExists(lockPath)) {
                    JCRNodeWrapper lock = session.getNode(lockPath);
                    if (server.equals(leaseOf(lock).owner())) {
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
