package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.imports.SystemNames;
import org.jahia.modules.formidable.engine.imports.model.ImportedAction;
import org.jahia.modules.formidable.engine.imports.model.ImportedContainer;
import org.jahia.modules.formidable.engine.imports.model.ImportedElement;
import org.jahia.modules.formidable.engine.imports.model.ImportedField;
import org.jahia.modules.formidable.engine.imports.model.ImportedForm;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    /** The value a consent stores, and the one option of an accept-terms checkbox without choices. */
    static final String ACCEPTED = "true";
    private static final String SAVE_ACTION_NAME = "save-to-jcr";
    private static final String EMAIL_ACTION_NAME = "email-notification";
    private static final String ACTION = "action ";
    private static final String FIELD = "field ";
    private static final String PLAIN_LANGUAGE = "";

    /** The Forms file-type groups a {@code fileValidation} selects, as {@code accept} can say them; {@code all} restricts nothing. */
    private static final Map<String, String> FILE_TYPES = Map.of(
            "image", "image/*", "audio", "audio/*", "video", "video/*", "pdf", "application/pdf", "text", "text/plain");
    private static final String ALL_FILE_TYPES = "all";
    /** The braced text of a terms label, which the accept-terms box rendered as the link to the terms. */
    private static final java.util.regex.Pattern LINK_TEXT = java.util.regex.Pattern.compile("\\{([^}]*)\\}");

    private final Predicate<String> registeredTypes;
    private final Predicate<String> declaredOptionsSources;
    private final boolean captchaConfigured;

    /**
     * @param registeredTypes whether the repository registers a node type: decides the extended inputs
     * @param declaredOptionsSources whether the instance declares an options source by key: decides the country field
     * @param captchaConfigured whether the instance configures a captcha: decides whether a form that displayed
     *                          one gets its captcha, or a line in the report
     */
    public FormsFormConverter(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources, boolean captchaConfigured) {
        this.registeredTypes = registeredTypes;
        this.declaredOptionsSources = declaredOptionsSources;
        this.captchaConfigured = captchaConfigured;
    }

    /** A converter on an instance without a captcha: the forms that displayed one are reported. */
    public FormsFormConverter(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources) {
        this(registeredTypes, declaredOptionsSources, false);
    }

    /**
     * @param form the form as the export holds it
     * @param results its results entry, or null while the form was never published nor submitted
     */
    public ImportedForm convert(FormsForm form, FormsResults results) {
        SystemNames names = new SystemNames();
        List<String> report = new ArrayList<>();
        String buildingLang = languageOf(form.buildingLang());
        List<ImportedElement> elements = form.steps().size() == 1
                ? elementsOf(form.steps().get(0), results, buildingLang, names, report)
                : stepsOf(form, results, buildingLang, names, report);
        reportFormSettings(form, report);
        return new ImportedForm(form.name(), titlesOf(form, buildingLang), form.buildingLang(), nonBlank(form.afterSubmissionText()),
                buttonLabels(form), form.settings().displaysCaptcha() && captchaConfigured, elements, actionsOf(form, report),
                SOURCE_SYSTEM, form.uuid(), results == null ? null : results.uuid(), relativePath(form.path()), report);
    }

    /** The language the names are generated in; the plain language, the site's default, when the source names none. */
    private static String languageOf(String buildingLang) {
        return buildingLang == null || buildingLang.isBlank() ? PLAIN_LANGUAGE : buildingLang;
    }

    /**
     * A form that Forms deleted, known by its results entry alone: one text field per label node, or a
     * select when the label node carries choices.
     */
    public ImportedForm convertFromLabels(FormsResults results) {
        SystemNames names = new SystemNames();
        String buildingLang = languageOf(results.buildingLang());
        List<ImportedElement> fields = new ArrayList<>();
        for (FormsLabel label : results.labels().values()) {
            Map<String, String> titles = FormsLabels.titles(null, label, label.name());
            String name = names.of(titles.get(buildingLang), label.name());
            String type = label.hasChoices() ? FormsFieldTypes.SELECT : FormsFieldTypes.INPUT_TEXT;
            ImportedField.Builder field = ImportedField.builder(name, type, FormsFieldTypes.accepted(type));
            if (label.hasChoices()) {
                field.options(FormsChoices.options(label.choices()));
            }
            fields.add(field.titles(titles).source(label.fieldId(), label.name(), null).build());
        }
        String parentName = results.parentFormName();
        Map<String, String> titles = results.titles().values().stream().anyMatch(t -> t != null && !t.isBlank())
                ? nonBlank(results.titles()) : Map.of(buildingLang, results.name());
        return new ImportedForm(results.name(), titles, results.buildingLang(), Map.of(), Map.of(), false, fields,
                List.of(), SOURCE_SYSTEM, null, results.uuid(), parentName == null ? null : FormsExport.FORMS_NODE + "/" + parentName,
                List.of("built from the labels of its results: Forms no longer holds the form itself"));
    }

    private List<ImportedElement> stepsOf(FormsForm form, FormsResults results, String buildingLang, SystemNames names, List<String> report) {
        List<ImportedElement> steps = new ArrayList<>();
        for (FormsStep step : form.steps()) {
            String name = names.reserve(step.name());
            steps.add(new ImportedContainer(name, ImportedContainer.STEP, nonBlank(step.titles()),
                    elementsOf(step, results, buildingLang, names, report)));
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
        ImportedField.Builder field = ImportedField
                .builder(names.of(titles.get(buildingLang), definition.name()), mapping.nodeType(), mapping.accepted())
                .titles(titles)
                .source(definition.uuid(), definition.name(), definition.type());
        if (mapping.note() != null) {
            field.report(mapping.note());
        }
        fillCommon(field, definition, mapping);
        fillRules(field, definition, mapping);
        fillByKind(field, definition, label, titles, buildingLang, mapping);
        return Optional.of(field.build());
    }

    private static void fillCommon(ImportedField.Builder field, FormsField definition, FormsFieldTypes.Mapping mapping) {
        // a select has no placeholder, but shows its empty option's label where a placeholder would stand
        String placeholderSlot = mapping.is(FormsFieldTypes.SELECT) ? FormsFieldTypes.OPTIONS_EMPTY_LABEL : FormsFieldTypes.PLACEHOLDER;
        field.i18nProperty(placeholderSlot, valuesOf(definition.option(FormsOptionNames.PLACEHOLDER)));
        field.i18nProperty(FormsFieldTypes.HELP_TEXT, valuesOf(definition.option(FormsOptionNames.HELP_TEXT)));
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
                field.report("rule " + rule.type() + " not carried over: " + mapping.nodeType() + " has no such rule"
                        + (rule.messages().isEmpty() ? "" : ", nor its message"));
            }
        }
    }

    /** Writes the settings and the message of a rule the Formidable field can carry; false when it cannot. */
    private static boolean carried(ImportedField.Builder field, FormsValidation rule, FormsFieldTypes.Mapping mapping) {
        switch (rule.type()) {
            case FormsValidation.REQUIRED -> {
                field.required(true);
                message(field, rule, FormsFieldTypes.MSG_VALUE_MISSING);
            }
            case FormsValidation.EMAIL -> {
                if (!mapping.is(FormsFieldTypes.INPUT_EMAIL)) {
                    return false;
                }
                message(field, rule, FormsFieldTypes.MSG_TYPE_MISMATCH);
            }
            case FormsValidation.RANGE_LENGTH -> {
                if (!field.accepts(FormsFieldTypes.MIN_LENGTH)) {
                    return false;
                }
                bounds(field, rule, FormsFieldTypes.MIN_LENGTH, FormsFieldTypes.MAX_LENGTH);
                message(field, rule, FormsFieldTypes.MSG_TOO_SHORT, FormsFieldTypes.MSG_TOO_LONG);
            }
            case FormsValidation.RANGE -> {
                if (!field.accepts(FormsFieldTypes.MIN_VALUE)) {
                    return false;
                }
                bounds(field, rule, FormsFieldTypes.MIN_VALUE, FormsFieldTypes.MAX_VALUE);
                message(field, rule, FormsFieldTypes.MSG_RANGE_UNDERFLOW, FormsFieldTypes.MSG_RANGE_OVERFLOW);
            }
            case FormsValidation.REGEX -> {
                if (!field.accepts(FormsFieldTypes.PATTERN)) {
                    return false;
                }
                field.property(FormsFieldTypes.PATTERN, plain(rule, FormsOptionNames.REGEX));
                message(field, rule, FormsFieldTypes.MSG_PATTERN_MISMATCH);
            }
            case FormsValidation.FILE -> {
                if (!mapping.is(FormsFieldTypes.INPUT_FILE)) {
                    return false;
                }
                acceptedFiles(field, rule);
                noMessage(field, rule);
            }
            case FormsValidation.FILE_NUMBER -> {
                if (!mapping.is(FormsFieldTypes.INPUT_FILE)) {
                    return false;
                }
                field.property(FormsFieldTypes.MULTIPLE, severalFiles(rule));
                noMessage(field, rule);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void bounds(ImportedField.Builder field, FormsValidation rule, String minProperty, String maxProperty) {
        field.property(minProperty, plain(rule, FormsOptionNames.MIN));
        field.property(maxProperty, plain(rule, FormsOptionNames.MAX));
    }

    /** The message of the rule, per language, into each slot the type has for it. */
    private static void message(ImportedField.Builder field, FormsValidation rule, String... slots) {
        Map<String, String> messages = rule.messages();
        if (messages.isEmpty()) {
            return;
        }
        for (String slot : slots) {
            field.i18nProperty(slot, messages);
        }
    }

    /** A rule whose settings carry but whose custom message has no slot on the type. */
    private static void noMessage(ImportedField.Builder field, FormsValidation rule) {
        if (!rule.messages().isEmpty()) {
            field.report("message of rule " + rule.type() + " not carried over: the field type has no slot for it");
        }
    }

    private static String severalFiles(FormsValidation rule) {
        String number = plain(rule, FormsOptionNames.FILE_NUMBER);
        return number != null && !"1".equals(number.trim()) ? "true" : null;
    }

    /**
     * The {@code accept} of the file field from the groups the Forms rule selected, a JSON list of
     * {@code {key, value, selected}} where the value is a MIME regular expression ({@code fileValidation.wzd}):
     * the groups {@code accept} can say are written, {@code all} restricts nothing, and the others, such as
     * {@code doc}, a regular expression over the office types, are reported.
     */
    private static void acceptedFiles(ImportedField.Builder field, FormsValidation rule) {
        String json = plain(rule, FormsOptionNames.FILE_TYPE);
        if (json == null || json.isBlank()) {
            return;
        }
        List<String> accept = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        try {
            JSONArray groups = new JSONArray(json);
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group == null || !group.optBoolean("selected")) {
                    continue;
                }
                String key = group.optString("key");
                if (ALL_FILE_TYPES.equals(key)) {
                    return;
                }
                String mapped = FILE_TYPES.get(key);
                if (mapped != null) {
                    accept.add(mapped);
                } else {
                    unmapped.add(key + " (" + group.optString("value") + ")");
                }
            }
        } catch (JSONException e) {
            field.report("file types not carried over, set accept by hand: " + json);
            return;
        }
        if (!accept.isEmpty()) {
            field.property(FormsFieldTypes.ACCEPT, String.join(",", accept));
        }
        if (!unmapped.isEmpty()) {
            field.report("file types not carried over, set accept by hand: " + String.join(", ", unmapped));
        }
    }

    private void fillByKind(ImportedField.Builder field, FormsField definition, FormsLabel label,
                            Map<String, String> titles, String buildingLang, FormsFieldTypes.Mapping mapping) {
        String kind = definition.kind();
        if (FormsFieldTypes.isMultipleChoice(kind) && mapping.is(FormsFieldTypes.SELECT)) {
            field.property(FormsFieldTypes.MULTIPLE, "true");
        }
        if (mapping.is(FormsFieldTypes.SELECT) || mapping.is(FormsFieldTypes.RADIO) || mapping.is(FormsFieldTypes.CHECKBOX)) {
            fillOptions(field, definition, label, kind, titles, buildingLang);
        }
        switch (kind) {
            case "textArea" -> field.property(FormsFieldTypes.ROWS, plain(definition, FormsOptionNames.ROWS));
            case "hidden" -> field.property(FormsFieldTypes.VALUE, plain(definition, FormsOptionNames.VALUE));
            case "switch" -> {
                if (mapping.is(FormsFieldTypes.SWITCH)) {
                    field.i18nProperty(FormsFieldTypes.ON_LABEL, valuesOf(definition.option(FormsOptionNames.TEXT_ON)));
                    field.i18nProperty(FormsFieldTypes.OFF_LABEL, valuesOf(definition.option(FormsOptionNames.TEXT_OFF)));
                } else {
                    field.options(trueFalseOptions(definition, titles.keySet()));
                }
            }
            case "rating" -> field.property(FormsFieldTypes.MAX_VALUE, plain(definition, FormsOptionNames.MAX));
            case "acceptTermCheckbox" -> {
                if (mapping.is(FormsFieldTypes.CONSENT)) {
                    field.i18nProperty(FormsFieldTypes.STATEMENT, statementOf(field, definition, titles));
                }
            }
            default -> {
                // nothing more for the other kinds
            }
        }
    }

    private void fillOptions(ImportedField.Builder field, FormsField definition, FormsLabel label, String kind,
                             Map<String, String> titles, String buildingLang) {
        if ("countryList".equals(kind) && declaredOptionsSources.test(COUNTRY_SOURCE)) {
            field.optionsSourceKey(COUNTRY_SOURCE);
            return;
        }
        Map<String, List<String>> options = optionsOf(definition, label);
        if (!options.isEmpty()) {
            field.options(options);
        } else if ("acceptTermCheckbox".equals(kind)) {
            // no choices to take the stored value from: the one option is the accepted value, and the
            // answers are rewritten to it as for a consent (FormsSubmissionConverter)
            String language = titles.containsKey(buildingLang) || titles.isEmpty() ? buildingLang : titles.keySet().iterator().next();
            field.options(Map.of(language, List.of(FormsChoices.option(ACCEPTED, titles.getOrDefault(language, ACCEPTED)))));
        } else {
            field.report("no choices found in the export: add the options by hand"
                    + ("countryList".equals(kind) ? ", or declare a country options source" : ""));
        }
    }

    /** The choices of the definition, per language, else those of the label node. */
    private static Map<String, List<String>> optionsOf(FormsField definition, FormsLabel label) {
        for (FormsOption choices : definition.choiceOptions()) {
            Map<String, List<String>> options = FormsChoices.options(choices.values().isEmpty() && choices.value() != null
                    ? Map.of(PLAIN_LANGUAGE, choices.value()) : choices.values());
            if (!options.isEmpty()) {
                return options;
            }
        }
        return label == null ? Map.of() : FormsChoices.options(label.choices());
    }

    /**
     * The statement of a consent: the terms label of the box, as its visitors read it. The braces of the
     * label mark the text the box rendered as a link to the terms file ({@code firstPart <a>hrefLabel</a>
     * lastPart} in the accept-terms directive): the text stays, the braces go, and the file, a repository
     * path the statement cannot hold, is reported. The title of the field when the box has no terms label.
     */
    private static Map<String, String> statementOf(ImportedField.Builder field, FormsField definition, Map<String, String> titles) {
        Map<String, String> terms = valuesOf(definition.option(FormsOptionNames.TERMS_LABEL));
        if (terms.isEmpty()) {
            return titles;
        }
        Map<String, String> statement = new LinkedHashMap<>();
        terms.forEach((language, text) -> statement.put(language, LINK_TEXT.matcher(text).replaceAll("$1").trim()));
        String link = plain(definition, FormsOptionNames.LINK);
        if (link != null && !link.isBlank()) {
            field.report("the link to the terms (" + link.trim() + ") not carried over: the statement holds no link, add one by hand");
        }
        return statement;
    }

    private static Map<String, List<String>> trueFalseOptions(FormsField definition, Iterable<String> languages) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (String language : languages) {
            String on = definition.option(FormsOptionNames.TEXT_ON) == null ? null : definition.option(FormsOptionNames.TEXT_ON).in(language);
            String off = definition.option(FormsOptionNames.TEXT_OFF) == null ? null : definition.option(FormsOptionNames.TEXT_OFF).in(language);
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

    /**
     * The notification keeps the {@code to} recipients alone: Formidable sends one message to one list,
     * so an address Forms kept in CC or BCC would be shown to every other recipient.
     */
    private static ImportedAction emailNotification(FormsAction action, List<String> report) {
        Map<String, String> properties = new LinkedHashMap<>();
        putPlain(properties, "to", emails(plain(action, FormsOptionNames.TO)));
        putPlain(properties, "from", emails(plain(action, FormsOptionNames.FROM)));
        String copies = copies(action);
        if (copies != null) {
            report.add(ACTION + action.type() + ": Forms also sent this mail in CC/BCC to " + copies
                    + "; Formidable sends to one list, add them by hand if they may be visible to the other recipients");
        }
        report.add(ACTION + action.type() + ": the body of the mail was not carried over, the two templates differ");
        return new ImportedAction(EMAIL_ACTION_NAME, FmdbNodeType.EMAIL_NOTIFICATION_ACTION, properties,
                nonBlankI18n(Map.of("subject", valuesOf(action.option(FormsOptionNames.SUBJECT)))));
    }

    private static String copies(FormsAction action) {
        List<String> all = new ArrayList<>();
        for (String option : List.of(FormsOptionNames.CC, FormsOptionNames.BCC)) {
            String value = emails(plain(action, option));
            if (value != null) {
                all.add(value);
            }
        }
        return all.isEmpty() ? null : String.join(", ", all);
    }

    /**
     * The addresses of an e-mail option as Forms reads them ({@code SendEmailAction.getEmailsFromStringTable}):
     * the brackets and quotes of a value stored as a JSON list of strings are dropped, a plain value is kept.
     */
    static String emails(String stored) {
        if (stored == null) {
            return null;
        }
        String addresses = stored.replace("[", "").replace("]", "").replace("\"", "").trim();
        return addresses.isEmpty() ? null : addresses;
    }

    private static String redirectTarget(FormsAction action) {
        String target = plain(action, FormsOptionNames.REDIRECT_TO);
        return target == null || target.isBlank() ? "unknown" : target;
    }

    private void reportFormSettings(FormsForm form, List<String> report) {
        FormsForm.Settings settings = form.settings();
        if (settings.savable()) {
            report.add("\"save the form for later\" not carried over: Formidable has no such feature");
        }
        if (settings.constrained()) {
            report.add("submission constraints not carried over: Formidable has no such feature");
        }
        if (settings.displaysCaptcha()) {
            report.add(captchaConfigured
                    ? "the form displayed a captcha: turned on, with the captcha this instance configures"
                    : "the form displayed a captcha: not turned on, this instance configures no captcha; set one up, then add the captcha to the form");
        }
    }

    /** The title of a plain button fills the submit label; the triple button's labels wait for spike 4 of the spec. */
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

    private static Map<String, String> titlesOf(FormsForm form, String buildingLang) {
        Map<String, String> titles = nonBlank(form.titles());
        return titles.isEmpty() ? Map.of(buildingLang, form.name()) : titles;
    }

    static String relativePath(String exportPath) {
        int slash = exportPath.indexOf('/');
        return slash < 0 ? exportPath : exportPath.substring(slash + 1);
    }

    private static String plain(FormsField definition, String option) {
        FormsOption found = definition.option(option);
        return found == null ? null : found.in(PLAIN_LANGUAGE);
    }

    private static String plain(FormsValidation rule, String option) {
        FormsOption found = rule.option(option);
        return found == null ? null : found.in(PLAIN_LANGUAGE);
    }

    private static String plain(FormsAction action, String option) {
        FormsOption found = action.option(option);
        return found == null ? null : found.in(PLAIN_LANGUAGE);
    }

    private static Map<String, String> valuesOf(FormsOption option) {
        if (option == null) {
            return Map.of();
        }
        return option.values().isEmpty() && option.value() != null ? Map.of(PLAIN_LANGUAGE, option.value()) : nonBlank(option.values());
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
