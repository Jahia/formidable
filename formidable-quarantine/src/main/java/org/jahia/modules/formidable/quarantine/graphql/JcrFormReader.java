package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.quarantine.run.JcrCheckList;
import org.jahia.modules.formidable.quarantine.store.QuarantineTree;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the live form with a system session, after the resolver checked the permission of the user on the site. A
 * node of another site, or a node that is not a form, gives {@link FormFacts#NONE}: the identifier comes from the
 * quarantine tree of the site, so this never happens but to a damaged tree.
 */
final class JcrFormReader implements FormReader {

    private static final String LIVE_WORKSPACE = "live";
    private static final String TITLE = "jcr:title";
    private static final String DEFAULT_LANGUAGE = "j:defaultLanguage";
    /** The node of the translation of a language, as a session with no locale returns it. */
    private static final String TRANSLATION_PREFIX = "j:translation_";

    @Override
    public FormFacts read(String siteKey, String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> facts(session, siteKey, formId));
    }

    static FormFacts facts(JCRSessionWrapper session, String siteKey, String formId) throws RepositoryException {
        JCRNodeWrapper form;
        try {
            form = session.getNodeByIdentifier(formId);
        } catch (ItemNotFoundException e) {
            return FormFacts.NONE;
        }
        String sitePath = QuarantineTree.sitePath(siteKey);
        if (!form.getPath().startsWith(sitePath + "/") || !form.isNodeType(FmdbMixin.FORM_ROOT)) {
            return FormFacts.NONE;
        }
        boolean showValues = form.hasProperty(FormFacts.SHOW_VALUES)
                && form.getProperty(FormFacts.SHOW_VALUES).getBoolean();
        return new FormFacts(title(form, session.getNode(sitePath)), showValues, checkTypes(form));
    }

    /** The title in the default language of the site, else a title with no language, else the name of the node. */
    private static String title(JCRNodeWrapper form, JCRNodeWrapper site) throws RepositoryException {
        String language = site.hasProperty(DEFAULT_LANGUAGE) ? site.getProperty(DEFAULT_LANGUAGE).getString() : null;
        if (language != null && form.hasNode(TRANSLATION_PREFIX + language)) {
            JCRNodeWrapper translation = form.getNode(TRANSLATION_PREFIX + language);
            if (translation.hasProperty(TITLE)) {
                return translation.getProperty(TITLE).getString();
            }
        }
        return form.hasProperty(TITLE) ? form.getProperty(TITLE).getString() : form.getName();
    }

    private static Map<String, String> checkTypes(JCRNodeWrapper form) throws RepositoryException {
        Map<String, String> types = new LinkedHashMap<>();
        if (form.hasNode(JcrCheckList.QUALIFIERS)) {
            for (JCRNodeWrapper check : form.getNode(JcrCheckList.QUALIFIERS).getNodes()) {
                if (check.isNodeType(FmdbMixin.QUALIFIER)) {
                    types.put(check.getName(), check.getPrimaryNodeTypeName());
                }
            }
        }
        return types;
    }
}
