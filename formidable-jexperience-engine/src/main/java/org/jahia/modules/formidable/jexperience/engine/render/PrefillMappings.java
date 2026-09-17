package org.jahia.modules.formidable.jexperience.engine.render;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.jexperience.engine.field.SensitiveField;
import org.jahia.modules.formidable.jexperience.engine.model.JxpMixin;
import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.modules.formidable.jexperience.engine.util.Json;
import org.jahia.modules.formidable.jexperience.engine.util.Sql2;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fields of a form the page prefills from the visitor profile, read in live from the JCR alone:
 * a field carrying the mapping mixin with a property, the prefill mixin, and not marked sensitive.
 * Never jCustomer — a render is not the place for a network call, and a property the schema no longer
 * offers simply comes back absent from the tracker's context. One entry per field name: the profile
 * property, and whether the profile's value may replace a default value the author gave the field.
 *
 * <p>Every mappable field of the form is read, mapped or not, and handed back as a dependency: the
 * block is written into the form's cached fragment, which the cache flushes when a node it depends on
 * changes — and the form's own node is not what changes when an author maps a field, switches its
 * prefill on or ticks its override. Without the fields as dependencies, the block would say what the
 * form mapped at its last cache miss, until the form itself was republished.</p>
 */
class PrefillMappings {

    /** What one field needs: the profile property it reads, and whether it may replace the author's default. */
    record Entry(String property, boolean overridesDefault) {
    }

    /** The fields to prefill, and the paths of every mappable field the answer was read from. */
    record Prefill(Map<String, Entry> entries, List<String> dependencies) {
    }

    Prefill read(JCRSessionWrapper session, JCRNodeWrapper form) throws RepositoryException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        List<String> dependencies = new ArrayList<>();
        NodeIterator fields = mappableFields(session, form);
        while (fields.hasNext()) {
            JCRNodeWrapper field = (JCRNodeWrapper) fields.nextNode();
            dependencies.add(field.getPath());
            if (field.isNodeType(JxpMixin.PREFILL) && field.isNodeType(JxpMixin.MAPPING)
                    && SensitiveField.isMapped(field) && !SensitiveField.isSensitive(field)) {
                entries.put(field.getName(), new Entry(field.getPropertyAsString(JxpProperty.PROFILE_PROPERTY),
                        flag(field, JxpProperty.PREFILL_OVERRIDES_DEFAULT)));
            }
        }
        return new Prefill(entries, dependencies);
    }

    /** Every field under the form that can be mapped, mapped or not — a seam for the tests, which have no query engine. */
    NodeIterator mappableFields(JCRSessionWrapper session, JCRNodeWrapper form) throws RepositoryException {
        return session.getWorkspace().getQueryManager()
                .createQuery(Sql2.descendantsOf(FmdbMixin.PROFILE_MAPPABLE_FIELD, form.getPath()), Query.JCR_SQL2).execute().getNodes();
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
