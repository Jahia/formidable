package org.jahia.modules.formidable.accounts.choicelist;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Feeds the field properties of the two actions ({@code choicelist[formidableAccountsFields='<kind>']}) with the
 * fields of the form the action sits in, so a contributor picks "the e-mail field" from a list instead of typing a name.
 * The stored value is the field's node name, which is what the submission is keyed by.
 *
 * <p>The {@code kind} parameter filters the list: {@code email} the fields carrying {@code fmdbmix:emailField},
 * {@code sensitive} the fields carrying {@code fmdbmix:sensitiveField} (a password input), {@code any} every other
 * field that submits a value. The form is found by walking up from the context node (the action being edited, or the
 * actions list it is being created in). Threading: a stateless singleton.</p>
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormFieldsInitializer implements ModuleChoiceListInitializer {

    /** The key used in the CND: {@code choicelist[formidableAccountsFields='email']}. */
    public static final String KEY = "formidableAccountsFields";

    private static final Logger log = LoggerFactory.getLogger(FormFieldsInitializer.class);
    private static final int MAX_DEPTH = 12;

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values,
                                                     Locale locale, Map<String, Object> context) {
        Object contextNode = context == null ? null : context.get("contextNode");
        if (!(contextNode instanceof JCRNodeWrapper start)) {
            return List.of();
        }
        List<ChoiceListValue> fields = new ArrayList<>();
        try {
            JCRNodeWrapper form = formOf(start);
            if (form != null) {
                collect(form, param == null ? "any" : param, fields, new java.util.HashSet<>(), 0);
            }
        } catch (RepositoryException e) {
            log.warn("[formidable-accounts] The fields of the form could not be listed.", e);
        }
        return fields;
    }

    private static JCRNodeWrapper formOf(JCRNodeWrapper start) throws RepositoryException {
        JCRNodeWrapper node = start;
        for (int i = 0; i < MAX_DEPTH && node != null; i++) {
            if (node.isNodeType(FmdbMixin.FORM_ROOT)) {
                return node;
            }
            try {
                node = node.getParent();
            } catch (ItemNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    private static void collect(JCRNodeWrapper node, String kind, List<ChoiceListValue> out, Set<String> seen, int depth)
            throws RepositoryException {
        if (depth > MAX_DEPTH) {
            return;
        }
        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            if (!(child instanceof JCRNodeWrapper field)) {
                continue;
            }
            if (field.isNodeType(FmdbMixin.FORM_ELEMENT) && !field.isNodeType(FmdbMixin.NON_SUBMITTABLE)
                    && !field.isNodeType(FmdbMixin.FILE_FIELD) && matches(field, kind) && seen.add(field.getName())) {
                out.add(new ChoiceListValue(label(field), field.getName()));
            }
            collect(field, kind, out, seen, depth + 1);
        }
    }

    private static boolean matches(JCRNodeWrapper field, String kind) throws RepositoryException {
        boolean sensitive = field.isNodeType(FmdbMixin.SENSITIVE_FIELD);
        return switch (kind) {
            case "sensitive" -> sensitive;
            case "email" -> !sensitive && field.isNodeType(FmdbMixin.EMAIL_FIELD);
            default -> !sensitive;
        };
    }

    private static String label(JCRNodeWrapper field) {
        String title = field.getDisplayableName();
        return title == null || title.isBlank() || title.equals(field.getName()) ? field.getName() : title + " (" + field.getName() + ")";
    }

    @Override
    public void setKey(String key) {
        // fixed key
    }

    @Override
    public String getKey() {
        return KEY;
    }
}
