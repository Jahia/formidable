package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.imports.SystemNames;
import org.jahia.modules.formidable.engine.imports.model.ImportedAction;
import org.jahia.modules.formidable.engine.imports.model.ImportedContainer;
import org.jahia.modules.formidable.engine.imports.model.ImportedElement;
import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Turns a Forms form into the form the import creates (docs/architecture/forms-import.md, "The forms",
 * "The fields", "The labels", "The system names", "The actions"). What Formidable cannot carry is written
 * into the report of the form or of the field, never silently dropped.
 */
public final class FormsFormConverter {

    public static final String SOURCE_SYSTEM = "jahia-forms";
    static final String COUNTRY_SOURCE = "country";
    private static final String SAVE_ACTION_NAME = "save-to-jcr";
    private static final String EMAIL_ACTION_NAME = "email-notification";
    private static final String ACTION = "action ";
    private static final String FIELD = "field ";

    private final Predicate<String> registeredTypes;
    private final Predicate<String> declaredOptionsSources;

    /**
     * @param registeredTypes whether the repository registers a node type: decides the extended inputs
     * @param declaredOptionsSources whether the instance declares an options source by key: decides the country field
     */
    public FormsFormConverter(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources) {
        this.registeredTypes = registeredTypes;
        this.declaredOptionsSources = declaredOptionsSources;
    }

    /**
     * @param form the form as the export holds it
     * @param results its results entry, or null while the form was never published nor submitted
     */
    public ImportedForm convert(FormsForm form, FormsResults results) {
        SystemNames names = new SystemNames();
        List<String> report = new ArrayList<>();
        String buildingLang = form.buildingLang();
        List<ImportedElement> elements = form.steps().size() == 1
                ? elementsOf(form.steps().get(0), results, buildingLang, names, report)
                : stepsOf(form, results, names, report);
        reportFormSettings(form, report);
        return new ImportedForm(form.name(), titlesOf(form), buildingLang, nonBlank(form.afterSubmissionText()),
                buttonLabels(form), form.settings().displaysCaptcha(), elements, actionsOf(form, report),
                SOURCE_SYSTEM, form.uuid(), results == null ? null : results.uuid(), relativePath(form.path()), report);
    }

    /**
     * A form that Forms deleted, known by its results entry alone: one text field per label node, or a
     * select when the label node carries choices.
     */
    public ImportedForm convertFromLabels(FormsResults results) {
        SystemNames names = new SystemNames();
        List<ImportedElement> fields = new ArrayList<>();
        for (FormsLabel label : results.labels().values()) {
            Map<String, String> titles = FormsLabels.titles(null, label, label.name());
            String name = names.of(titles.get(results.buildingLang()), label.name());
            ImportedField.Builder field = label.hasChoices()
                    ? ImportedField.builder(name, FormsFieldTypes.SELECT).options(FormsChoices.options(label.choices()))
                    : ImportedField.builder(name, FormsFieldTypes.INPUT_TEXT);
            fields.add(field.titles(titles).source(label.fieldId(), label.name(), null).build());
        }
        String parentName = results.parentFormName();
        Map<String, String> titles = results.titles().values().stream().anyMatch(t -> t != null && !t.isBlank())
                ? nonBlank(results.titles()) : Map.of(results.buildingLang(), results.name());
        return new ImportedForm(results.name(), titles, results.buildingLang(), Map.of(), Map.of(), false, fields,
                List.of(), SOURCE_SYSTEM, null, results.uuid(), parentName == null ? null : FormsExport.FORMS_NODE + "/" + parentName,
                List.of("built from the labels of its results: Forms no longer holds the form itself"));
    }

    private List<ImportedElement> stepsOf(FormsForm form, FormsResults results, SystemNames names, List<String> report) {
        List<ImportedElement> steps = new ArrayList<>();
        for (FormsStep step : form.steps()) {
            String name = names.reserve(step.name());
            steps.add(new ImportedContainer(name, ImportedContainer.STEP, nonBlank(step.titles()),
                    elementsOf(step, results, form.buildingLang(), names, report)));
        }
        return steps;
    }

    /** The fields of a step, the ones between a fieldset start and its end wrapped in a fieldset. */
    private List<ImportedElement> elementsOf(FormsStep step, FormsResults results, String buildingLang,
                                            SystemNames names, List<String> report) {
        Deque<Fieldset> open = new ArrayDeque<>();
        List<ImportedElement> top = new ArrayList<>();
        for (FormsField definition : step.fields()) {
            String kind = definition.kind();
            List<ImportedElement> target = open.isEmpty() ? top : open.peek().children;
            if (FormsFieldTypes.isFieldsetStart(kind)) {
                Map<String, String> titles = FormsLabels.titles(definition, null, definition.name());
                open.push(new Fieldset(names.of(titles.get(buildingLang), definition.name()), titles));
            } else if (FormsFieldTypes.isFieldsetEnd(kind)) {
                if (!open.isEmpty()) {
                    Fieldset closed = open.pop();
                    (open.isEmpty() ? top : open.peek().children).add(closed.toContainer());
                }
            } else if (!FormsFieldTypes.isButton(kind)) {
                fieldOf(definition, results, buildingLang, names, report).ifPresent(target::add);
            }
        }
        while (!open.isEmpty()) {
            top.add(open.pop().toContainer());
        }
        return top;
    }

    private Optional<ImportedField> fieldOf(FormsField definition, FormsResults results, String buildingLang,
                                                     SystemNames names, List<String> report) {
        FormsFieldTypes.Mapping mapping = FormsFieldTypes.of(definition, registeredTypes);
        if (mapping == null) {
            report.add(notRecreated(definition));
            return Optional.empty();
        }
        FormsLabel label = labelOf(results, definition);
        Map<String, String> titles = FormsLabels.titles(definition, label, definition.name());
        ImportedField.Builder field = ImportedField.builder(names.of(titles.get(buildingLang), definition.name()), mapping.nodeType())
                .titles(titles)
                .source(definition.uuid(), definition.name(), definition.type());
        if (mapping.note() != null) {
            field.report(mapping.note());
        }
        fillCommon(field, definition);
        fillRules(field, definition, mapping);
        fillByKind(field, definition, label, titles, mapping);
        return Optional.of(field.build());
    }

    private static void fillCommon(ImportedField.Builder field, FormsField definition) {
        field.i18nProperty("placeholder", valuesOf(definition.option(FormsOptionNames.PLACEHOLDER)));
        field.i18nProperty("helpText", valuesOf(definition.option(FormsOptionNames.HELP_TEXT)));
        field.required(definition.validation(FormsValidation.REQUIRED).isPresent());
        if (definition.prefilled()) {
            field.report("prefill not carried over: Formidable prefills come from the visitor profile mapping, set it by hand");
        }
        if (definition.hasLogic()) {
            field.report("conditional logic not carried over: rebuild it by hand");
        }
    }

    private static void fillRules(ImportedField.Builder field, FormsField definition, FormsFieldTypes.Mapping mapping) {
        for (FormsValidation rule : definition.validations()) {
            if (!carried(field, rule, mapping)) {
                field.report("rule " + rule.type() + " not carried over: the field type has no such rule");
            }
        }
    }

    /** Writes the settings of a rule the Formidable field can carry; false when it cannot. */
    private static boolean carried(ImportedField.Builder field, FormsValidation rule, FormsFieldTypes.Mapping mapping) {
        return switch (rule.type()) {
            case FormsValidation.REQUIRED, FormsValidation.EMAIL -> true; // required is a flag; an email field validates itself
            case FormsValidation.RANGE_LENGTH -> isText(mapping) && bounds(field, rule, "minLength", "maxLength");
            case FormsValidation.RANGE -> mapping.is(FormsFieldTypes.INPUT_NUMBER) && bounds(field, rule, "minValue", "maxValue");
            case FormsValidation.REGEX -> isText(mapping) && set(field, "pattern", plain(rule, FormsOptionNames.REGEX));
            case FormsValidation.FILE -> mapping.is(FormsFieldTypes.INPUT_FILE) && set(field, "accept", plain(rule, FormsOptionNames.FILE_TYPE));
            case FormsValidation.FILE_NUMBER -> mapping.is(FormsFieldTypes.INPUT_FILE) && set(field, "multiple", severalFiles(rule));
            default -> false;
        };
    }

    private static boolean bounds(ImportedField.Builder field, FormsValidation rule, String minProperty, String maxProperty) {
        set(field, minProperty, plain(rule, FormsOptionNames.MIN));
        set(field, maxProperty, plain(rule, FormsOptionNames.MAX));
        return true;
    }

    private static boolean set(ImportedField.Builder field, String property, String value) {
        field.property(property, value);
        return true;
    }

    private static String severalFiles(FormsValidation rule) {
        String number = plain(rule, FormsOptionNames.FILE_NUMBER);
        return number != null && !"1".equals(number.trim()) ? "true" : null;
    }

    private void fillByKind(ImportedField.Builder field, FormsField definition, FormsLabel label,
                            Map<String, String> titles, FormsFieldTypes.Mapping mapping) {
        String kind = definition.kind();
        if (FormsFieldTypes.isMultipleChoice(kind) && mapping.is(FormsFieldTypes.SELECT)) {
            field.property("multiple", "true");
        }
        if (mapping.is(FormsFieldTypes.SELECT) || mapping.is(FormsFieldTypes.RADIO) || mapping.is(FormsFieldTypes.CHECKBOX)) {
            fillOptions(field, definition, label, kind);
        }
        switch (kind) {
            case "textArea" -> field.property("rows", plain(definition, FormsOptionNames.ROWS));
            case "hidden" -> field.property("value", plain(definition, FormsOptionNames.VALUE));
            case "switch" -> {
                field.i18nProperty("onLabel", valuesOf(definition.option(FormsOptionNames.ON_LABEL)));
                field.i18nProperty("offLabel", valuesOf(definition.option(FormsOptionNames.OFF_LABEL)));
                if (mapping.is(FormsFieldTypes.RADIO)) {
                    field.options(trueFalseOptions(definition, titles.keySet()));
                }
            }
            case "rating" -> field.property("maxValue", plain(definition, FormsOptionNames.MAX));
            case "acceptTermCheckbox" -> {
                if (mapping.is(FormsFieldTypes.CONSENT)) {
                    field.i18nProperty("statement", titles);
                }
            }
            default -> {
                // nothing more for the other kinds
            }
        }
    }

    private void fillOptions(ImportedField.Builder field, FormsField definition, FormsLabel label, String kind) {
        if ("countryList".equals(kind) && declaredOptionsSources.test(COUNTRY_SOURCE)) {
            field.optionsSourceKey(COUNTRY_SOURCE);
            return;
        }
        Map<String, List<String>> options = optionsOf(definition, label);
        if (options.isEmpty()) {
            if ("acceptTermCheckbox".equals(kind)) {
                field.options(Map.of(definition.titles().isEmpty() ? "en" : definition.titles().keySet().iterator().next(),
                        List.of(FormsChoices.option("true", "accepted"))));
            } else {
                field.report("no choices found in the export: add the options by hand"
                        + ("countryList".equals(kind) ? ", or declare a country options source" : ""));
            }
            return;
        }
        field.options(options);
    }

    /** The choices of the definition, per language, else those of the label node. */
    private static Map<String, List<String>> optionsOf(FormsField definition, FormsLabel label) {
        for (FormsOption choices : definition.choiceOptions()) {
            Map<String, List<String>> options = FormsChoices.options(choices.values().isEmpty() && choices.value() != null
                    ? Map.of("", choices.value()) : choices.values());
            if (!options.isEmpty()) {
                return options;
            }
        }
        return label == null ? Map.of() : FormsChoices.options(label.choices());
    }

    private static Map<String, List<String>> trueFalseOptions(FormsField definition, Iterable<String> languages) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (String language : languages) {
            String on = definition.option(FormsOptionNames.ON_LABEL) == null ? null : definition.option(FormsOptionNames.ON_LABEL).in(language);
            String off = definition.option(FormsOptionNames.OFF_LABEL) == null ? null : definition.option(FormsOptionNames.OFF_LABEL).in(language);
            options.put(language, List.of(
                    FormsChoices.option("true", on == null || on.isBlank() ? "true" : on),
                    FormsChoices.option("false", off == null || off.isBlank() ? "false" : off)));
        }
        return options;
    }

    private static List<ImportedAction> actionsOf(FormsForm form, List<String> report) {
        List<ImportedAction> actions = new ArrayList<>();
        boolean saved = false;
        for (FormsAction action : form.actions()) {
            if (action.is(FormsAction.SAVE_TO_JCR)) {
                actions.add(new ImportedAction(SAVE_ACTION_NAME, FmdbNodeType.SAVE_TO_JCR_ACTION, Map.of(), Map.of()));
                saved = true;
            } else if (action.is(FormsAction.SEND_EMAIL)) {
                actions.add(emailNotification(action, report));
            } else if (action.is(FormsAction.SEND_EMAIL_TO_SUBMITTER)) {
                actions.add(new ImportedAction(EMAIL_ACTION_NAME + "-submitter", FmdbNodeType.EMAIL_NOTIFICATION_ACTION,
                        Map.of(), nonBlankI18n(Map.of("subject", valuesOf(action.option(FormsOptionNames.SUBJECT))))));
                report.add(ACTION + action.type() + ": Formidable has no action that writes to the submitter; "
                        + "a notification without recipient was created, complete or remove it");
            } else if (action.is(FormsAction.REDIRECT_TO_PAGE) || action.is(FormsAction.REDIRECT_TO_URL)) {
                report.add(ACTION + action.type() + " not carried over (target: " + redirectTarget(action)
                        + "): Formidable has no redirect action yet");
            } else {
                report.add(ACTION + action.type() + " not carried over: Formidable has no equivalent");
            }
        }
        if (!saved) {
            report.add("Forms did not save this form's submissions; add Save to JCR to keep saving them");
        }
        return actions;
    }

    private static ImportedAction emailNotification(FormsAction action, List<String> report) {
        Map<String, String> properties = new LinkedHashMap<>();
        putPlain(properties, "to", recipients(action));
        putPlain(properties, "from", plain(action, FormsOptionNames.FROM));
        report.add(ACTION + action.type() + ": the body of the mail was not carried over, the two templates differ");
        return new ImportedAction(EMAIL_ACTION_NAME, FmdbNodeType.EMAIL_NOTIFICATION_ACTION, properties,
                nonBlankI18n(Map.of("subject", valuesOf(action.option(FormsOptionNames.SUBJECT)))));
    }

    private static String recipients(FormsAction action) {
        List<String> all = new ArrayList<>();
        for (String option : List.of(FormsOptionNames.TO, FormsOptionNames.CC, FormsOptionNames.BCC)) {
            String value = plain(action, option);
            if (value != null && !value.isBlank()) {
                all.add(value.trim());
            }
        }
        return all.isEmpty() ? null : String.join(",", all);
    }

    private static String redirectTarget(FormsAction action) {
        String target = plain(action, FormsOptionNames.REDIRECT_TO);
        if (target == null) {
            target = plain(action, FormsOptionNames.URL);
        }
        return target == null ? "unknown" : target;
    }

    private static void reportFormSettings(FormsForm form, List<String> report) {
        FormsForm.Settings settings = form.settings();
        if (settings.savable()) {
            report.add("\"save the form for later\" not carried over: Formidable has no such feature");
        }
        if (settings.constrained()) {
            report.add("submission constraints not carried over: Formidable has no such feature");
        }
        if (settings.displaysCaptcha()) {
            report.add("the form displayed a captcha: turned on when the instance configures one, else to set up");
        }
    }

    private static Map<String, Map<String, String>> buttonLabels(FormsForm form) {
        Map<String, Map<String, String>> labels = new LinkedHashMap<>();
        for (FormsField definition : form.fields()) {
            if (FormsFieldTypes.isButton(definition.kind())) {
                Map<String, String> title = nonBlank(definition.titles());
                if (!title.isEmpty()) {
                    labels.putIfAbsent("submitBtnLabel", title);
                }
            }
        }
        return labels;
    }

    private static String notRecreated(FormsField definition) {
        return switch (definition.kind()) {
            case "password" -> FIELD + definition.name() + " (password) not recreated: Formidable would store and mail the password in clear";
            case "contentDisplay" -> FIELD + definition.name() + " (content display) not recreated: it displays a content, submits nothing";
            default -> FIELD + definition.name() + " (" + definition.type() + ") not recreated";
        };
    }

    private static FormsLabel labelOf(FormsResults results, FormsField definition) {
        if (results == null) {
            return null;
        }
        return results.labels().values().stream()
                .filter(l -> definition.uuid() != null && definition.uuid().equals(l.fieldId()))
                .findFirst()
                .orElse(results.label(definition.name()));
    }

    private static Map<String, String> titlesOf(FormsForm form) {
        Map<String, String> titles = nonBlank(form.titles());
        return titles.isEmpty() ? Map.of(form.buildingLang(), form.name()) : titles;
    }

    static String relativePath(String exportPath) {
        int slash = exportPath.indexOf('/');
        return slash < 0 ? exportPath : exportPath.substring(slash + 1);
    }

    private static boolean isText(FormsFieldTypes.Mapping mapping) {
        return mapping.is(FormsFieldTypes.INPUT_TEXT) || mapping.is(FormsFieldTypes.INPUT_EMAIL) || mapping.is(FormsFieldTypes.TEXTAREA);
    }

    private static String plain(FormsField definition, String option) {
        FormsOption found = definition.option(option);
        return found == null ? null : found.in("");
    }

    private static String plain(FormsValidation rule, String option) {
        FormsOption found = rule.option(option);
        return found == null ? null : found.in("");
    }

    private static String plain(FormsAction action, String option) {
        FormsOption found = action.option(option);
        return found == null ? null : found.in("");
    }

    private static Map<String, String> valuesOf(FormsOption option) {
        if (option == null) {
            return Map.of();
        }
        return option.values().isEmpty() && option.value() != null ? Map.of("", option.value()) : nonBlank(option.values());
    }

    private static void putPlain(Map<String, String> properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    private static Map<String, String> nonBlank(Map<String, String> byLanguage) {
        Map<String, String> kept = new LinkedHashMap<>();
        byLanguage.forEach((language, value) -> {
            if (value != null && !value.isBlank()) {
                kept.put(language, value);
            }
        });
        return kept;
    }

    private static Map<String, Map<String, String>> nonBlankI18n(Map<String, Map<String, String>> properties) {
        Map<String, Map<String, String>> kept = new LinkedHashMap<>();
        properties.forEach((key, byLanguage) -> {
            if (!byLanguage.isEmpty()) {
                kept.put(key, byLanguage);
            }
        });
        return kept;
    }

    /** A fieldset being filled while its fields are read. */
    private static final class Fieldset {
        private final String name;
        private final Map<String, String> titles;
        private final List<ImportedElement> children = new ArrayList<>();

        Fieldset(String name, Map<String, String> titles) {
            this.name = name;
            this.titles = titles;
        }

        ImportedContainer toContainer() {
            return new ImportedContainer(name, ImportedContainer.FIELDSET, titles, children);
        }
    }
}
