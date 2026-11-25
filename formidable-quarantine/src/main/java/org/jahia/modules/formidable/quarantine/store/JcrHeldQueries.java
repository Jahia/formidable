package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.RowIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The {@link HeldQueries} of the module: JCR-SQL2 queries on {@code fmdbq:heldSubmission} below the quarantine root
 * of the site, in live, with a system session. Every value that the caller gives, the site key, the form and the
 * states, enters the query as a bound variable. The path of the root is the one literal: it is built from a site key
 * of letters, digits, dashes and underscores, and refused otherwise.
 */
@Component(service = HeldQueries.class, immediate = true)
public class JcrHeldQueries implements HeldQueries {

    static final String SITE_VARIABLE = "site";
    static final String FORM_VARIABLE = "form";
    static final String STATE_VARIABLE = "state";
    /** The characters of a path of a quarantine root: no quote, no bracket, nothing that ends the literal. */
    private static final Pattern ROOT_PATH = Pattern.compile("/sites/[A-Za-z0-9_-]{1,64}/" + QuarantineTree.ROOT_NAME);

    /** Runs a statement with its bound values: the seam a unit test replaces. */
    interface Runner {
        long count(String statement, Map<String, String> values) throws RepositoryException;

        List<HeldRecord> page(String statement, Map<String, String> values, int offset, int limit)
                throws RepositoryException;
    }

    private final Runner runner;

    public JcrHeldQueries() {
        this.runner = new JcrRunner();
    }

    JcrHeldQueries(Runner runner) {
        this.runner = runner;
    }

    @Override
    public Page page(String siteKey, String formId, Set<String> states, int offset, int limit)
            throws QuarantineStoreException {
        List<String> ordered = new ArrayList<>(new TreeSet<>(states));
        String statement = statement(siteKey, formId != null, ordered.size());
        Map<String, String> values = values(siteKey, formId, ordered);
        try {
            long total = runner.count(statement, values);
            List<HeldRecord> records = total == 0 || limit == 0 || offset >= total ? List.of()
                    : runner.page(statement, values, offset, limit);
            return new Page(total, records);
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("Could not query the held submissions of site " + siteKey, e);
        }
    }

    @Override
    public long count(String siteKey, String state) throws QuarantineStoreException {
        try {
            return runner.count(statement(siteKey, false, 1), values(siteKey, null, List.of(state)));
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("Could not count the held submissions of site " + siteKey, e);
        }
    }

    /**
     * The statement: the held submissions below the root of the site that name the site, of one form when asked, in
     * one of the states when asked, oldest first.
     */
    static String statement(String siteKey, boolean byForm, int states) {
        String root = QuarantineTree.rootPath(siteKey);
        if (!ROOT_PATH.matcher(root).matches()) {
            throw new IllegalArgumentException("Not a site key");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM [").append(QuarantineTree.HELD_TYPE)
                .append("] AS held WHERE ISDESCENDANTNODE(held, '").append(root).append("')")
                .append(" AND held.[").append(QuarantineTree.SITE_KEY).append("] = $").append(SITE_VARIABLE);
        if (byForm) {
            sql.append(" AND held.[").append(QuarantineTree.FORM_ID).append("] = $").append(FORM_VARIABLE);
        }
        for (int index = 0; index < states; index++) {
            sql.append(index == 0 ? " AND (" : " OR ").append("held.[").append(QuarantineTree.STATE).append("] = $")
                    .append(STATE_VARIABLE).append(index);
        }
        if (states > 0) {
            sql.append(')');
        }
        return sql.append(" ORDER BY held.[").append(QuarantineTree.CREATED_AT).append("] ASC").toString();
    }

    static Map<String, String> values(String siteKey, String formId, List<String> states) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(SITE_VARIABLE, siteKey);
        if (formId != null) {
            values.put(FORM_VARIABLE, formId);
        }
        for (int index = 0; index < states.size(); index++) {
            values.put(STATE_VARIABLE + index, states.get(index));
        }
        return values;
    }

    /** The runner on the repository: the live workspace, with a system session. Covered by the Cypress specs. */
    private static final class JcrRunner implements Runner {

        private static <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, JcrQuarantineStore.LIVE_WORKSPACE,
                    null, callback);
        }

        private static Query query(JCRSessionWrapper session, String statement, Map<String, String> values)
                throws RepositoryException {
            QueryManager manager = session.getWorkspace().getQueryManager();
            Query query = manager.createQuery(statement, Query.JCR_SQL2);
            ValueFactory factory = session.getValueFactory();
            for (Map.Entry<String, String> value : values.entrySet()) {
                query.bindValue(value.getKey(), factory.createValue(value.getValue()));
            }
            return query;
        }

        @Override
        public long count(String statement, Map<String, String> values) throws RepositoryException {
            return inLiveSystemSession(session -> {
                RowIterator rows = query(session, statement, values).execute().getRows();
                long size = rows.getSize();
                if (size >= 0) {
                    return size;
                }
                // the repository did not compute the size: the rows are counted, no node is read
                long counted = 0;
                while (rows.hasNext()) {
                    rows.nextRow();
                    counted++;
                }
                return counted;
            });
        }

        @Override
        public List<HeldRecord> page(String statement, Map<String, String> values, int offset, int limit)
                throws RepositoryException {
            return inLiveSystemSession(session -> {
                Query query = query(session, statement, values);
                query.setOffset(offset);
                query.setLimit(limit);
                List<HeldRecord> records = new ArrayList<>();
                NodeIterator nodes = query.execute().getNodes();
                while (nodes.hasNext()) {
                    records.add(JcrQuarantineStore.recordOf((JCRNodeWrapper) nodes.nextNode()));
                }
                return records;
            });
        }
    }
}
