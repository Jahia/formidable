package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.OnInconclusive;
import org.jahia.modules.formidable.quarantine.api.OnUnavailable;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Reads the "Checks" list of a form in live, with a system session. Every check of the list counts, whatever its
 * type: a check that no registered qualifier serves stops the run at review, it is never skipped. A check is a node
 * of the list with the mixin {@code fmdbmix:qualifier}. Any other child, such as the {@code j:translation_<lang>}
 * node that the session with no locale returns, is not a check.
 * <p>
 * The session has no locale: a check reads no internationalized property of its node.
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
    private static final String RESIDUAL = "*";
    /** The prefixes of the properties of the platform, which no check reads as a setting. */
    private static final List<String> PLATFORM_PREFIXES = List.of("jcr:", "j:", "mix:");

    @Override
    public List<CheckSettings> of(String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> settingsOf(session.getNodeByIdentifier(formId)));
    }

    /** The settings of each check of the list of the form, in the order of the list. */
    static List<CheckSettings> settingsOf(JCRNodeWrapper form) throws RepositoryException {
        return settingsOf(form, check -> check.getPrimaryNodeType().getPropertyDefinitions());
    }

    /** The property definitions of the primary type of a check node. A unit test hands its own. */
    @FunctionalInterface
    interface Definitions {
        PropertyDefinition[] of(JCRNodeWrapper check) throws RepositoryException;
    }

    static List<CheckSettings> settingsOf(JCRNodeWrapper form, Definitions definitions) throws RepositoryException {
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
                    OnUnavailable.fromValue(string(check, ON_UNAVAILABLE)),
                    propertiesOf(check, definitions.of(check))));
        }
        return settings;
    }

    /**
     * The properties of the type of the check that the node holds, as {@link CheckSettings#properties()} states: those
     * its primary type declares or inherits, but for the properties of the platform and the two common settings. A
     * multiple property is joined with {@link CheckSettings#VALUE_SEPARATOR}.
     */
    static Map<String, String> propertiesOf(JCRNodeWrapper check, PropertyDefinition[] definitions)
            throws RepositoryException {
        Map<String, String> properties = new LinkedHashMap<>();
        for (PropertyDefinition definition : definitions) {
            String name = definition.getName();
            if (isOwnProperty(name) && !properties.containsKey(name) && check.hasProperty(name)) {
                properties.put(name, valueOf(check.getProperty(name)));
            }
        }
        return properties;
    }

    /** False for a residual definition, a property of the platform and the two common settings. */
    static boolean isOwnProperty(String name) {
        return !RESIDUAL.equals(name) && !ON_INCONCLUSIVE.equals(name) && !ON_UNAVAILABLE.equals(name)
                && PLATFORM_PREFIXES.stream().noneMatch(name::startsWith);
    }

    private static String valueOf(JCRPropertyWrapper property) throws RepositoryException {
        if (!property.isMultiple()) {
            return property.getString();
        }
        StringJoiner joined = new StringJoiner(CheckSettings.VALUE_SEPARATOR);
        for (Value value : property.getValues()) {
            joined.add(value.getString());
        }
        return joined.toString();
    }

    private static String string(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.hasProperty(property) ? node.getProperty(property).getString() : null;
    }
}
