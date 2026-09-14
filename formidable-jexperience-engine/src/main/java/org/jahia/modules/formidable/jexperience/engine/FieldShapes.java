package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Infers a field's {@link FieldShape} from the semantic mixins the engine defines, never from
 * primary type names: a third-party field that opts into {@code fmdbmix:numberField} is treated
 * like the built-in number field. Cardinality follows the {@code multiple} boolean property —
 * the convention of the built-in select and email inputs, which a third-party type adopts by
 * declaring a property of that name; without it a field is single-valued. The table is the one
 * of the integration design (docs/architecture/jexperience-integration.md, "Type compatibility
 * in the dropdown").
 */
public final class FieldShapes {

    public static final String MAPPABLE_MARKER = "fmdbmix:profileMappableField";

    static final String FILE_FIELD = "fmdbmix:fileField";
    static final String EMAIL_FIELD = "fmdbmix:emailField";
    static final String CHOICE_FIELD = "fmdbmix:choiceField";
    static final String NUMBER_FIELD = "fmdbmix:numberField";
    static final String BOOLEAN_FIELD = "fmdbmix:booleanField";
    static final String DATE_FIELD = "fmdbmix:dateField";
    static final String DATETIME_LOCAL_FIELD = "fmdbmix:datetimeLocalField";
    static final String COLOR_FIELD = "fmdbmix:colorField";
    static final String TEXT_FIELD = "fmdbmix:textField";

    /**
     * The checkbox is the one choice field with no "multiple" property; it is classified as a
     * group, a list, whatever its option count. The renderer draws one input for a single option,
     * which then submits one value: a limit recorded in the design's compatibility table (the
     * count is unknown at edit time for a sourced list).
     */
    static final String CHECKBOX_TYPE = "fmdb:checkbox";
    static final String MULTIPLE_PROPERTY = "multiple";

    private static final List<String> RELEVANT_TYPES = List.of(MAPPABLE_MARKER, FILE_FIELD, EMAIL_FIELD, CHOICE_FIELD,
            NUMBER_FIELD, BOOLEAN_FIELD, DATE_FIELD, DATETIME_LOCAL_FIELD, COLOR_FIELD, TEXT_FIELD, CHECKBOX_TYPE);

    private static final Set<String> STRING = Set.of("string");
    private static final Set<String> EMAIL = Set.of("email", "string");
    private static final Set<String> NUMBER = Set.of("integer", "long", "float", "double");
    private static final Set<String> BOOLEAN = Set.of("boolean");
    private static final Set<String> DATE = Set.of("date");

    private FieldShapes() {
    }

    /** The shape of an existing field node, cardinality as stored; empty when the field is not mappable. */
    public static Optional<FieldShape> infer(JCRNodeWrapper node) throws RepositoryException {
        boolean multiple = node.hasProperty(MULTIPLE_PROPERTY) && node.getProperty(MULTIPLE_PROPERTY).getBoolean();
        return infer(node, multiple);
    }

    /**
     * The shape of an existing field node whose "multiple" toggle the editor holds unsaved: the
     * given value wins over the stored one.
     */
    public static Optional<FieldShape> infer(JCRNodeWrapper node, boolean multiple) throws RepositoryException {
        // JCR reads throw RepositoryException, which a predicate cannot: read the node once, up front
        Set<String> types = new HashSet<>();
        for (String type : RELEVANT_TYPES) {
            if (node.isNodeType(type)) {
                types.add(type);
            }
        }
        return infer(types::contains, name -> MULTIPLE_PROPERTY.equals(name) && multiple);
    }

    /**
     * The shape of a field that does not exist yet (the editor creating it): its type — the JCR
     * interface Jahia's ExtendedNodeType implements, which is all the rule needs — and the
     * "multiple" toggle as the editor holds it, false until the author switches it on.
     */
    public static Optional<FieldShape> infer(NodeType type, boolean multiple) {
        return infer(type::isNodeType, name -> MULTIPLE_PROPERTY.equals(name) && multiple);
    }

    static Optional<FieldShape> infer(Predicate<String> isNodeType, Predicate<String> flag) {
        if (!isNodeType.test(MAPPABLE_MARKER) || isNodeType.test(FILE_FIELD)) {
            return Optional.empty();
        }
        // the email input also carries fmdbmix:textField: the more specific kind wins
        if (isNodeType.test(EMAIL_FIELD)) {
            return Optional.of(new FieldShape(EMAIL, flag.test(MULTIPLE_PROPERTY)));
        }
        if (isNodeType.test(CHOICE_FIELD)) {
            return Optional.of(new FieldShape(STRING, isNodeType.test(CHECKBOX_TYPE) || flag.test(MULTIPLE_PROPERTY)));
        }
        if (isNodeType.test(NUMBER_FIELD)) {
            return Optional.of(new FieldShape(NUMBER, false));
        }
        if (isNodeType.test(BOOLEAN_FIELD)) {
            return Optional.of(new FieldShape(BOOLEAN, false));
        }
        if (isNodeType.test(DATE_FIELD) || isNodeType.test(DATETIME_LOCAL_FIELD)) {
            return Optional.of(new FieldShape(DATE, false));
        }
        // text, colour, and the kinds without a value mixin (the hidden input) hold a string
        return Optional.of(new FieldShape(STRING, false));
    }
}
