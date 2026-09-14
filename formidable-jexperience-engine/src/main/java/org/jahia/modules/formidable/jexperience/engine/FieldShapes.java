package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Infers a field's {@link FieldShape} from the semantic mixins the engine defines, never from
 * primary type names: a third-party field that opts into {@code fmdbmix:numberField} is treated
 * like the built-in number field. Cardinality follows the {@code multiple} boolean property —
 * the convention of the built-in select and email inputs, which a third-party type adopts by
 * declaring a property of that name; without it a field is single-valued. The checkbox is the
 * exception: its cardinality follows its number of choices, as the view renders it. The table is
 * the one of the integration design (docs/architecture/jexperience-integration.md, "Type
 * compatibility in the dropdown").
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
     * The checkbox is the one choice field with no "multiple" property: the renderer draws one
     * input, submitting one value, for exactly one choice, and a group otherwise — so does the
     * shape, from the same count (the engine's ChoiceOptionsResolver, or the options the editor
     * holds unsaved). A count the source cannot give is a group.
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

    /**
     * A choice count computed only when a rule needs it — the checkbox's: counting may resolve an
     * options source, a repository query for a content-sourced field, which must not be paid by
     * every mappable field the editor opens.
     */
    @FunctionalInterface
    public interface ChoiceCount {
        OptionalInt get() throws RepositoryException;

        static ChoiceCount of(OptionalInt count) {
            return () -> count;
        }
    }

    /**
     * The shape of an existing field node; empty when the field is not mappable.
     *
     * @param pendingMultiple the "multiple" toggle as the editor holds it unsaved, when it re-asks
     *                        the list for that change; empty on a plain opening, where the stored
     *                        value applies
     * @param choiceCount     how many choices a choice field offers, asked only for a checkbox
     */
    public static Optional<FieldShape> infer(JCRNodeWrapper node, Optional<Boolean> pendingMultiple, ChoiceCount choiceCount) throws RepositoryException {
        // JCR reads throw RepositoryException, which a predicate cannot: read the node once, up front
        Set<String> types = new HashSet<>();
        for (String type : RELEVANT_TYPES) {
            if (node.isNodeType(type)) {
                types.add(type);
            }
        }
        boolean multiple = pendingMultiple.isPresent()
                ? pendingMultiple.get()
                : node.hasProperty(MULTIPLE_PROPERTY) && node.getProperty(MULTIPLE_PROPERTY).getBoolean();
        return infer(types::contains, name -> MULTIPLE_PROPERTY.equals(name) && multiple, choiceCount);
    }

    /**
     * The shape of a field that does not exist yet (the editor creating it): its type — the JCR
     * interface Jahia's ExtendedNodeType implements, which is all the rule needs — the "multiple"
     * toggle as the editor holds it (false until the author switches it on), and the choices typed
     * so far.
     */
    public static Optional<FieldShape> infer(NodeType type, boolean multiple, ChoiceCount choiceCount) throws RepositoryException {
        return infer(type::isNodeType, name -> MULTIPLE_PROPERTY.equals(name) && multiple, choiceCount);
    }

    static Optional<FieldShape> infer(Predicate<String> isNodeType, Predicate<String> flag, ChoiceCount choiceCount) throws RepositoryException {
        if (!isNodeType.test(MAPPABLE_MARKER) || isNodeType.test(FILE_FIELD)) {
            return Optional.empty();
        }
        // the email input also carries fmdbmix:textField: the more specific kind wins
        if (isNodeType.test(EMAIL_FIELD)) {
            return Optional.of(new FieldShape(EMAIL, flag.test(MULTIPLE_PROPERTY)));
        }
        if (isNodeType.test(CHOICE_FIELD)) {
            // the count is asked here only: the other choice fields never need it
            boolean multivalued = isNodeType.test(CHECKBOX_TYPE) ? isAGroup(choiceCount.get()) : flag.test(MULTIPLE_PROPERTY);
            return Optional.of(new FieldShape(STRING, multivalued));
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

    /** The renderer's own rule: exactly one choice is one checkbox, one value; anything else, unknown included, is a group. */
    static boolean isAGroup(OptionalInt choiceCount) {
        return choiceCount.isEmpty() || choiceCount.getAsInt() != 1;
    }
}
