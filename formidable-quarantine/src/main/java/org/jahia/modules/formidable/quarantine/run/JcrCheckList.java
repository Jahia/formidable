package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.OnInconclusive;
import org.jahia.modules.formidable.quarantine.api.OnUnavailable;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the "Checks" list of a form in live, with a system session. Every check of the list counts, whatever its
 * type: a check that no registered qualifier serves stops the run at review, it is never skipped. A check is a node
 * of the list with the mixin {@code fmdbmix:qualifier}. Any other child, such as the {@code j:translation_<lang>}
 * node that the session with no locale returns, is not a check.
 */
public final class JcrCheckList implements CheckList {

    /**
     * The child of a form that holds its checks, which the engine declares on {@code fmdbmix:quarantine}. The
     * {@code api} package of the engine has no constant for it.
     */
    public static final String QUALIFIERS = "qualifiers";
    static final String ON_INCONCLUSIVE = "onInconclusive";
    static final String ON_UNAVAILABLE = "onUnavailable";
    private static final String LIVE_WORKSPACE = "live";

    @Override
    public List<CheckSettings> of(String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> settingsOf(session.getNodeByIdentifier(formId)));
    }

    /** The settings of each check of the list of the form, in the order of the list. */
    static List<CheckSettings> settingsOf(JCRNodeWrapper form) throws RepositoryException {
        List<CheckSettings> settings = new ArrayList<>();
        if (!form.hasNode(QUALIFIERS)) {
            return settings;
        }
        for (JCRNodeWrapper check : form.getNode(QUALIFIERS).getNodes()) {
            if (!check.isNodeType(FmdbMixin.QUALIFIER)) {
                continue;
            }
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
