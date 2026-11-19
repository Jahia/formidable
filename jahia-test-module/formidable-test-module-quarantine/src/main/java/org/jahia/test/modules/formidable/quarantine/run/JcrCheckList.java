package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.test.modules.formidable.quarantine.check.CheckSettings;
import org.jahia.test.modules.formidable.quarantine.check.OnInconclusive;
import org.jahia.test.modules.formidable.quarantine.check.OnUnavailable;
import org.jahia.test.modules.formidable.quarantine.hold.JcrFormChecks;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the "Checks" list of a form in live, with a system session. Every node of the list counts, whatever its
 * type: a node that no registered qualifier serves stops the run at review, it is never skipped.
 */
public final class JcrCheckList implements CheckList {

    static final String ON_INCONCLUSIVE = "onInconclusive";
    static final String ON_UNAVAILABLE = "onUnavailable";
    private static final String LIVE_WORKSPACE = "live";

    @Override
    public List<CheckSettings> of(String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> settingsOf(session.getNodeByIdentifier(formId)));
    }

    /** The settings of each node of the list of the form, in the order of the list. */
    static List<CheckSettings> settingsOf(JCRNodeWrapper form) throws RepositoryException {
        List<CheckSettings> settings = new ArrayList<>();
        if (!form.hasNode(JcrFormChecks.QUALIFIERS)) {
            return settings;
        }
        for (JCRNodeWrapper check : form.getNode(JcrFormChecks.QUALIFIERS).getNodes()) {
            settings.add(new CheckSettings(check.getPrimaryNodeTypeName(), check.getName(),
                    OnInconclusive.fromValue(string(check, ON_INCONCLUSIVE)),
                    OnUnavailable.fromValue(string(check, ON_UNAVAILABLE))));
        }
        return settings;
    }

    private static String string(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.hasProperty(property) ? node.getProperty(property).getString() : null;
    }
}
