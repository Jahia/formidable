package org.jahia.modules.formidable.jexperience.engine.render;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.jexperience.engine.util.Json;
import org.jahia.modules.formidable.jexperience.engine.util.Sql2;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The profile properties that a form's conditional logic reads, from the rules of the
 * {@value #SOURCE_TYPE} source, read in live from the JCR alone. The client script adds them to
 * the properties it asks the tracker for, the same request as the prefill. Names only: the
 * browser reads the values, and the server never asks jCustomer.
 *
 * <p>Every element of the form that can carry rules is a dependency of the fragment, whatever it
 * holds: an author who adds a rule on the profile changes the element, not the form.</p>
 */
class LogicProperties {

    private static final Logger log = LoggerFactory.getLogger(LogicProperties.class);

    /** The rule source this module serves: the id that formidable-elements and the editor give it. */
    static final String SOURCE_TYPE = "jexperience:profile";
    /** The key of a rule of that source that names the profile property. */
    static final String CONFIG_KEY = "property";
    /** The engine's property of {@code fmdbmix:formLogicElement} that holds the rules, one JSON object each. */
    static final String LOGICS = "logics";

    /** The properties, each once and in the form's order, and the paths of every element they were read from. */
    record Logic(List<String> properties, List<String> dependencies) {
    }

    Logic read(JCRSessionWrapper session, JCRNodeWrapper form) throws RepositoryException {
        List<String> properties = new ArrayList<>();
        List<String> dependencies = new ArrayList<>();
        NodeIterator elements = logicElements(session, form);
        while (elements.hasNext()) {
            JCRNodeWrapper element = (JCRNodeWrapper) elements.nextNode();
            dependencies.add(element.getPath());
            if (!element.hasProperty(LOGICS)) {
                continue;
            }
            for (Value value : element.getProperty(LOGICS).getValues()) {
                String property = propertyOf(value.getString(), element.getPath());
                if (property != null && !properties.contains(property)) {
                    properties.add(property);
                }
            }
        }
        return new Logic(properties, dependencies);
    }

    /**
     * The profile property a stored rule reads, trimmed as the browser trims it, or null when the
     * rule reads another source or names none. A rule that is not JSON is the engine's to report: it
     * fails closed there, and costs nothing here but a debug line.
     */
    static String propertyOf(String rule, String path) {
        try {
            JSONObject json = new JSONObject(rule);
            if (!SOURCE_TYPE.equals(json.optString("sourceType", ""))) {
                return null;
            }
            String property = json.optString(CONFIG_KEY, "").trim();
            return property.isEmpty() ? null : property;
        } catch (JSONException e) {
            log.debug("[LogicProperties] A rule of {} is not JSON, skipped: {}", path, e.getMessage());
            return null;
        }
    }

    /** Every element under the form that can carry rules — a seam for the tests, which have no query engine. */
    NodeIterator logicElements(JCRSessionWrapper session, JCRNodeWrapper form) throws RepositoryException {
        return session.getWorkspace().getQueryManager()
                .createQuery(Sql2.descendantsOf(FmdbMixin.FORM_LOGIC_ELEMENT, form.getPath()), Query.JCR_SQL2).execute().getNodes();
    }

    /** The JSON array the block carries: {@code ["country",…]}, {@code []} when no rule reads the profile. */
    static String json(List<String> properties) {
        return properties.stream().map(Json::string).collect(Collectors.joining(",", "[", "]"));
    }
}
