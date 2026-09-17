package org.jahia.modules.formidable.jexperience.engine.render;

import org.jahia.modules.formidable.jexperience.engine.field.SensitiveField;
import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.modules.formidable.jexperience.engine.rule.FormMappingReader;
import org.jahia.modules.formidable.jexperience.engine.util.Json;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fields of a form the page prefills from the visitor profile, read in live from the JCR alone:
 * a field carrying the mapping mixin with a property, its prefill switch on, and not marked sensitive.
 * Never jCustomer — a render is not the place for a network call, and a property the schema no longer
 * offers simply comes back absent from the tracker's context. One entry per field name: the profile
 * property, and whether the profile's value may replace a default value the author gave the field.
 */
class PrefillMappings {

    /** What one field needs: the profile property it reads, and whether it may replace the author's default. */
    record Entry(String property, boolean overridesDefault) {
    }

    Map<String, Entry> read(JCRSessionWrapper session, JCRNodeWrapper form) throws RepositoryException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        NodeIterator fields = mappedFields(session, form);
        while (fields.hasNext()) {
            JCRNodeWrapper field = (JCRNodeWrapper) fields.nextNode();
            if (SensitiveField.isMapped(field) && !SensitiveField.isSensitive(field) && flag(field, JxpProperty.PREFILL)) {
                entries.put(field.getName(), new Entry(field.getPropertyAsString(JxpProperty.PROFILE_PROPERTY),
                        flag(field, JxpProperty.PREFILL_OVERRIDES_DEFAULT)));
            }
        }
        return entries;
    }

    /** The fields under the form carrying the mapping mixin — the rule's own query; a seam for the tests. */
    NodeIterator mappedFields(JCRSessionWrapper session, JCRNodeWrapper form) throws RepositoryException {
        return session.getWorkspace().getQueryManager().createQuery(FormMappingReader.queryFor(form.getPath()), Query.JCR_SQL2).execute().getNodes();
    }

    private static boolean flag(JCRNodeWrapper field, String property) throws RepositoryException {
        return field.hasProperty(property) && field.getProperty(property).getBoolean();
    }

    /** The JSON object the block carries: {@code {"field":{"property":"…","overridesDefault":false},…}}, {@code {}} when nothing prefills. */
    static String json(Map<String, Entry> entries) {
        StringBuilder out = new StringBuilder("{");
        entries.forEach((field, entry) -> out.append(out.length() > 1 ? "," : "")
                .append(Json.string(field)).append(":{\"property\":").append(Json.string(entry.property()))
                .append(",\"overridesDefault\":").append(entry.overridesDefault()).append('}'));
        return out.append('}').toString();
    }
}
