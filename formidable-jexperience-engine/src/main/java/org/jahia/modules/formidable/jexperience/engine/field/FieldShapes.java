package org.jahia.modules.formidable.jexperience.engine.field;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Infers a field's {@link FieldShape} from the semantic mixins the engine defines, never from
 * primary type names: a third-party field that opts into {@code fmdbmix:numberField} is treated
 * like the built-in number field. Cardinality follows the {@code multiple} boolean property —
 * the convention of the built-in select and email inputs, which a third-party type adopts by
 * declaring a property of that name; without it a field is single-valued. The checkbox is the
 * exception: its cardinality follows its number of choices, as the view renders it. The table is
 * the one of the integration design (docs/architecture/jexperience-integration.md, "Type
 * compatibility in the dropdown").
 *
 * <p>A sensitive field ({@code fmdbmix:sensitiveField}, a password) is never mappable, whatever else its type declares:
 * sensitive wins. A type that declares the mappable marker too is told once, so that its developer learns why the
 * mapping is ignored.</p>
 */
public final class FieldShapes {

    public static final String MULTIPLE_PROPERTY = "multiple";

    private static final Logger log = LoggerFactory.getLogger(FieldShapes.class);

    /** The sensitive field types already reported as also declaring the mappable marker: said once per type. */
    static final Set<String> SENSITIVE_MAPPABLE_TYPES_REPORTED = ConcurrentHashMap.newKeySet();

    private static final List<String> RELEVANT_TYPES = List.of(FmdbMixin.SENSITIVE_FIELD, FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.FILE_FIELD,
            FmdbMixin.EMAIL_FIELD, FmdbMixin.CHOICE_FIELD, FmdbMixin.NUMBER_FIELD, FmdbMixin.BOOLEAN_FIELD, FmdbMixin.DATE_FIELD,
            FmdbMixin.DATETIME_LOCAL_FIELD, FmdbMixin.COLOR_FIELD, FmdbMixin.TEXT_FIELD, FmdbMixin.CARDINALITY_FROM_CHOICES);

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
        if (types.contains(FmdbMixin.SENSITIVE_FIELD)) {
            reportIfMappable(node.getPrimaryNodeTypeName(), types.contains(FmdbMixin.PROFILE_MAPPABLE_FIELD));
            return Optional.empty();
        }
        boolean multiple = pendingMultiple.isPresent()
                ? pendingMultiple.get()
                : node.hasProperty(MULTIPLE_PROPERTY) && node.getProperty(MULTIPLE_PROPERTY).getBoolean();
        return infer(types::contains, name -> MULTIPLE_PROPERTY.equals(name) && multiple, choiceCount);
    }

    /**
     * Whether the field is sensitive, which no reader of the mappable marker may map, prefill nor send: the marker's
     * own readers ask it before anything else.
     */
    public static boolean isSensitive(JCRNodeWrapper node) throws RepositoryException {
        if (!node.isNodeType(FmdbMixin.SENSITIVE_FIELD)) {
            return false;
        }
        reportIfMappable(node.getPrimaryNodeTypeName(), node.isNodeType(FmdbMixin.PROFILE_MAPPABLE_FIELD));
        return true;
    }

    /**
     * The shape of a field that does not exist yet (the editor creating it): its type — the JCR
     * interface Jahia's ExtendedNodeType implements, which is all the rule needs — the "multiple"
     * toggle as the editor holds it (false until the author switches it on), and the choices typed
     * so far.
     */
    public static Optional<FieldShape> infer(NodeType type, boolean multiple, ChoiceCount choiceCount) throws RepositoryException {
        if (type.isNodeType(FmdbMixin.SENSITIVE_FIELD)) {
            reportIfMappable(type.getName(), type.isNodeType(FmdbMixin.PROFILE_MAPPABLE_FIELD));
        }
        return infer(type::isNodeType, name -> MULTIPLE_PROPERTY.equals(name) && multiple, choiceCount);
    }

    static Optional<FieldShape> infer(Predicate<String> isNodeType, Predicate<String> flag, ChoiceCount choiceCount) throws RepositoryException {
        // sensitive wins: a secret is never mapped, whatever else the type declares
        if (isNodeType.test(FmdbMixin.SENSITIVE_FIELD)
                || !isNodeType.test(FmdbMixin.PROFILE_MAPPABLE_FIELD) || isNodeType.test(FmdbMixin.FILE_FIELD)) {
            return Optional.empty();
        }
        // the email input also carries fmdbmix:textField: the more specific kind wins
        if (isNodeType.test(FmdbMixin.EMAIL_FIELD)) {
            return Optional.of(new FieldShape(EMAIL, flag.test(MULTIPLE_PROPERTY)));
        }
        if (isNodeType.test(FmdbMixin.CHOICE_FIELD)) {
            // the count is asked here only: the other choice fields never need it
            boolean multivalued = isNodeType.test(FmdbMixin.CARDINALITY_FROM_CHOICES) ? isAGroup(choiceCount.get()) : flag.test(MULTIPLE_PROPERTY);
            return Optional.of(new FieldShape(STRING, multivalued));
        }
        if (isNodeType.test(FmdbMixin.NUMBER_FIELD)) {
            return Optional.of(new FieldShape(NUMBER, false));
        }
        if (isNodeType.test(FmdbMixin.BOOLEAN_FIELD)) {
            return Optional.of(new FieldShape(BOOLEAN, false));
        }
        if (isNodeType.test(FmdbMixin.DATE_FIELD) || isNodeType.test(FmdbMixin.DATETIME_LOCAL_FIELD)) {
            return Optional.of(new FieldShape(DATE, false));
        }
        // text, colour, and the kinds without a value mixin (the hidden input) hold a string
        return Optional.of(new FieldShape(STRING, false));
    }

    /** The one WARN of a sensitive type that also declares the mappable marker, once per type. */
    private static void reportIfMappable(String typeName, boolean mappable) {
        if (mappable && SENSITIVE_MAPPABLE_TYPES_REPORTED.add(String.valueOf(typeName))) {
            log.warn("[FieldShapes] Field type '{}' declares both {} and {}: a sensitive field is never mapped to the "
                    + "visitor profile, prefilled nor sent to jExperience, and its mapping is ignored. Remove {} from the type.",
                    typeName, FmdbMixin.SENSITIVE_FIELD, FmdbMixin.PROFILE_MAPPABLE_FIELD, FmdbMixin.PROFILE_MAPPABLE_FIELD);
        }
    }

    /** The renderer's own rule: exactly one choice is one checkbox, one value; anything else, unknown included, is a group. */
    static boolean isAGroup(OptionalInt choiceCount) {
        return choiceCount.isEmpty() || choiceCount.getAsInt() != 1;
    }
}
