package org.jahia.modules.formidable.content.plan;

import org.jahia.modules.formidable.content.convert.ConversionException;
import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.modules.formidable.content.convert.Reference;
import org.jahia.modules.formidable.content.convert.TypedValue;
import org.jahia.modules.formidable.content.convert.ValueConverter;
import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.jahia.modules.formidable.content.mapping.PairRule;
import org.jahia.modules.formidable.content.platform.TypeCatalog;
import org.jahia.modules.formidable.engine.api.SubmittedFile;

import javax.jcr.PropertyType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the settings of the action and one submission into a {@link ContentPlan}, checking every mapping against the
 * node types before anything is written. Pure apart from the {@link TypeCatalog} it asks. Stateless and thread-safe.
 *
 * <p>Two kinds of failure, kept apart because the visitor is told different things: a {@link ConfigException} means
 * the author's settings are wrong (an unknown property, a type that cannot be created: logged, the form is
 * "unavailable"); a {@link ValueException} means the submission is wrong (a bad value, a video without captions).</p>
 *
 * <p>Precedence when several settings fill the same property, later wins: the form's fields, then the fixed values,
 * then the submitter, the organization and the page. What the visitor typed can never override what the platform knows.</p>
 */
public final class PlanBuilder {

    /** The longest title kept, which is also what names the node. */
    public static final int MAX_TITLE = 512;

    private static final String FILE_TYPE = "jnt:file";
    private static final String TITLE = "jcr:title";

    /**
     * What was submitted.
     *
     * @param parameters the accepted values of the non-sensitive fields, by field name
     * @param files      the validated uploads
     */
    public record Submission(Map<String, List<String>> parameters, List<SubmittedFile> files) {
        public Submission {
            parameters = parameters == null ? Map.of() : parameters;
            files = files == null ? List.of() : List.copyOf(files);
        }
    }

    /**
     * Who submitted.
     *
     * @param username     the signed-in user's name
     * @param organization the user's organization, may be {@code null}
     */
    public record Identity(String username, String organization) {
    }

    /**
     * Where it goes, after the submitter's subfolder was applied.
     *
     * @param parentPath the folder for the content
     * @param filesPath  the folder for the files, {@code null} when the action stores none
     */
    public record Target(String parentPath, String filesPath) {
    }

    private PlanBuilder() {
    }

    /**
     * Builds the plan.
     *
     * @param settings       the parsed settings of the action
     * @param submission     the values and files
     * @param who            the submitter
     * @param pageIdentifier the identifier of the page the form was submitted from, or {@code null}
     * @param target         the folders
     * @param languages      the site's languages, the default first
     * @param catalog        the node types
     * @return the plan
     * @throws ConfigException when the settings do not fit the node types
     * @throws ValueException  when the submission does not fit the settings
     */
    public static ContentPlan build(Settings settings, Submission submission, Identity who, String pageIdentifier, Target target,
                                    List<Locale> languages, TypeCatalog catalog) throws ConfigException, ValueException {
        Optional<PairRule> broken = PairRule.firstBroken(settings.pairs(), submission.files());
        if (broken.isPresent()) {
            PairRule rule = broken.get();
            throw new ValueException(ValueException.Kind.PAIR,
                    "a file of '" + rule.field() + "' matching " + rule.mimePattern() + " has no companion in '" + rule.dependentField() + "'", rule);
        }
        String type = settings.contentType();
        if (!catalog.isCreatable(type)) {
            throw new ConfigException("The content type '" + type + "' does not exist or cannot be created (abstract or a mixin)");
        }
        if (settings.fileMixin() != null && !catalog.isMixin(settings.fileMixin())) {
            throw new ConfigException("The file mixin '" + settings.fileMixin() + "' does not exist or is not a mixin");
        }
        List<String> contentTypes = List.of(type);
        List<String> fileTypes = settings.fileMixin() == null ? List.of(FILE_TYPE) : List.of(FILE_TYPE, settings.fileMixin());

        String title = title(settings, submission, catalog, contentTypes);
        Map<String, TypedValue> properties = new LinkedHashMap<>();
        fieldValues(settings, submission, catalog, contentTypes, properties);
        fixedValues(settings, catalog, contentTypes, properties);
        submitterValues(settings, who, pageIdentifier, catalog, contentTypes, properties);
        List<FileGroup> groups = fileGroups(settings, submission, target, catalog, contentTypes);
        List<TypedValue> fileProperties = fileProperties(settings, submission, catalog, fileTypes);

        return new ContentPlan(type, target.parentPath(), target.filesPath(), title,
                new ArrayList<>(properties.values()), groups, settings.fileMixin(), fileProperties, settings.publish(), languages);
    }

    private static String title(Settings settings, Submission submission, TypeCatalog catalog, List<String> types) throws ConfigException {
        if (settings.titleField() == null) {
            return null;
        }
        if (catalog.property(types, TITLE).isEmpty()) {
            throw new ConfigException("The content type '" + settings.contentType() + "' has no jcr:title: a title field cannot be used (add mix:title to the type)");
        }
        String value = first(submission.parameters().get(settings.titleField()));
        if (value == null) {
            return null;
        }
        return value.length() > MAX_TITLE ? value.substring(0, MAX_TITLE) : value;
    }

    private static void fieldValues(Settings settings, Submission submission, TypeCatalog catalog, List<String> types,
                                    Map<String, TypedValue> into) throws ConfigException, ValueException {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fieldMappings()) {
            PropertyShape shape = shapeOf(catalog, types, entry.right(), "fieldMappings");
            if (shape.isReference()) {
                throw new ConfigException("Setting 'fieldMappings': '" + entry.right() + "' holds references: fill it with a file mapping or the page setting");
            }
            requireFillable(shape, "fieldMappings");
            if (!shape.multiple() && seen.put(entry.right(), entry.left()) != null) {
                throw new ConfigException("Setting 'fieldMappings': the single-valued property '" + entry.right() + "' is mapped from two fields");
            }
            try {
                List<Object> values = ValueConverter.convert(shape, submission.parameters().get(entry.left()));
                if (!values.isEmpty()) {
                    merge(into, shape, values);
                }
            } catch (ConversionException e) {
                throw new ValueException(ValueException.Kind.BAD_VALUE, "field '" + entry.left() + "': " + e.getMessage());
            }
        }
    }

    private static void fixedValues(Settings settings, TypeCatalog catalog, List<String> types, Map<String, TypedValue> into)
            throws ConfigException {
        Map<String, TypedValue> fixed = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fixedValues()) {
            PropertyShape shape = shapeOf(catalog, types, entry.left(), "fixedValues");
            if (shape.isReference()) {
                throw new ConfigException("Setting 'fixedValues': '" + entry.left() + "' holds references and cannot take a fixed text");
            }
            requireFillable(shape, "fixedValues");
            try {
                List<Object> values = ValueConverter.convert(shape, List.of(entry.right()));
                if (!shape.multiple() && fixed.containsKey(entry.left())) {
                    throw new ConfigException("Setting 'fixedValues': the single-valued property '" + entry.left() + "' is given two values");
                }
                merge(fixed, shape, values);
            } catch (ConversionException e) {
                throw new ConfigException("Setting 'fixedValues': " + e.getMessage(), e);
            }
        }
        into.putAll(fixed);
    }

    private static void submitterValues(Settings settings, Identity who, String pageIdentifier, TypeCatalog catalog, List<String> types,
                                        Map<String, TypedValue> into) throws ConfigException {
        if (settings.submitterProperty() != null) {
            PropertyShape shape = singleString(catalog, types, settings.submitterProperty(), "submitterProperty");
            into.put(shape.name(), new TypedValue(shape, List.of(who.username())));
        }
        if (settings.organizationProperty() != null) {
            PropertyShape shape = singleString(catalog, types, settings.organizationProperty(), "organizationProperty");
            if (who.organization() != null && !who.organization().isBlank()) {
                into.put(shape.name(), new TypedValue(shape, List.of(who.organization())));
            }
        }
        if (settings.pageProperty() != null) {
            PropertyShape shape = shapeOf(catalog, types, settings.pageProperty(), "pageProperty");
            if (!shape.isReference() || shape.multiple()) {
                throw new ConfigException("Setting 'pageProperty': '" + settings.pageProperty() + "' must be a single reference or weakreference property");
            }
            if (pageIdentifier != null) {
                into.put(shape.name(), new TypedValue(shape, List.of(new Reference(pageIdentifier))));
            }
        }
    }

    private static List<FileGroup> fileGroups(Settings settings, Submission submission, Target target, TypeCatalog catalog,
                                              List<String> types) throws ConfigException, ValueException {
        Map<String, PropertyShape> shapes = new LinkedHashMap<>();
        Map<String, List<SubmittedFile>> files = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fileMappings()) {
            PropertyShape shape = shapeOf(catalog, types, entry.right(), "fileMappings");
            if (!shape.isReference()) {
                throw new ConfigException("Setting 'fileMappings': '" + entry.right() + "' must be a reference or weakreference property");
            }
            shapes.putIfAbsent(entry.right(), shape);
            for (SubmittedFile file : submission.files()) {
                if (entry.left().equals(file.fieldName())) {
                    files.computeIfAbsent(entry.right(), key -> new ArrayList<>()).add(file);
                }
            }
        }
        List<FileGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<SubmittedFile>> group : files.entrySet()) {
            PropertyShape shape = shapes.get(group.getKey());
            if (!shape.multiple() && group.getValue().size() > 1) {
                throw new ValueException(ValueException.Kind.BAD_VALUE, "property '" + shape.name() + "' holds one file and the form sent " + group.getValue().size());
            }
            groups.add(new FileGroup(shape, group.getValue()));
        }
        if (!groups.isEmpty() && target.filesPath() == null) {
            throw new ConfigException("Files were uploaded but the action has no filesFolder");
        }
        return groups;
    }

    private static List<TypedValue> fileProperties(Settings settings, Submission submission, TypeCatalog catalog, List<String> fileTypes)
            throws ConfigException, ValueException {
        Map<String, TypedValue> values = new LinkedHashMap<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Mappings.Entry entry : settings.fileProperties()) {
            PropertyShape shape = shapeOf(catalog, fileTypes, entry.right(), "fileProperties");
            if (shape.isReference()) {
                throw new ConfigException("Setting 'fileProperties': '" + entry.right() + "' holds references and cannot take a field's text");
            }
            requireFillable(shape, "fileProperties");
            if (!shape.multiple() && seen.put(entry.right(), entry.left()) != null) {
                throw new ConfigException("Setting 'fileProperties': the single-valued property '" + entry.right() + "' is mapped from two fields");
            }
            try {
                List<Object> converted = ValueConverter.convert(shape, submission.parameters().get(entry.left()));
                if (!converted.isEmpty()) {
                    merge(values, shape, converted);
                }
            } catch (ConversionException e) {
                throw new ValueException(ValueException.Kind.BAD_VALUE, "field '" + entry.left() + "' for a file property: " + e.getMessage());
            }
        }
        return new ArrayList<>(values.values());
    }

    private static PropertyShape shapeOf(TypeCatalog catalog, List<String> types, String property, String setting) throws ConfigException {
        return catalog.property(types, property).orElseThrow(() ->
                new ConfigException("Setting '" + setting + "': the property '" + property + "' is not declared by " + types
                        + " (or it is protected): check its name and prefix"));
    }

    private static void requireFillable(PropertyShape shape, String setting) throws ConfigException {
        if (!ValueConverter.supports(shape.type())) {
            throw new ConfigException("Setting '" + setting + "': the property '" + shape.name() + "' has the type "
                    + PropertyType.nameFromValue(shape.type()) + ", which cannot be filled from text");
        }
    }

    private static PropertyShape singleString(TypeCatalog catalog, List<String> types, String property, String setting) throws ConfigException {
        PropertyShape shape = shapeOf(catalog, types, property, setting);
        if (shape.type() != PropertyType.STRING || shape.multiple()) {
            throw new ConfigException("Setting '" + setting + "': '" + property + "' must be a single-valued string property");
        }
        return shape;
    }

    private static void merge(Map<String, TypedValue> into, PropertyShape shape, List<Object> values) {
        TypedValue existing = into.get(shape.name());
        if (existing == null || !shape.multiple()) {
            into.put(shape.name(), new TypedValue(shape, values));
            return;
        }
        List<Object> all = new ArrayList<>(existing.values());
        all.addAll(values);
        into.put(shape.name(), new TypedValue(shape, all));
    }

    private static String first(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).findFirst().orElse(null);
    }
}
