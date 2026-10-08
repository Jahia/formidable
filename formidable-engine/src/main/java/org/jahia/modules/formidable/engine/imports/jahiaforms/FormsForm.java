package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One Forms form ({@code fcnt:form}) under {@code formFactory/forms}, with its steps and actions.
 *
 * @param path the path in the export, {@code formFactory/forms/contact-us}, what the results reference
 * @param settings the form-level settings Forms keeps in its mixins
 */
record FormsForm(String name, String uuid, String path, String buildingLang, Map<String, String> titles,
                 Map<String, String> afterSubmissionText, Settings settings, List<FormsStep> steps,
                 List<FormsAction> actions) {

    static final String TYPE = "fcnt:form";
    private static final String BUILDING_LANG = "buildingLang";
    private static final String AFTER_SUBMISSION_TEXT = "afterSubmissionText";

    /**
     * The settings of a form. Forms gives every form all of its mixins, so a mixin says nothing: the
     * flags are the properties, {@code displayCaptcha}, {@code trackUser}, {@code isFormSavable}, and a
     * submission constraint is set when a start date, an end date or a maximum of submissions is.
     */
    record Settings(boolean displaysCaptcha, boolean tracksUsers, boolean savable, boolean constrained) {

        static final Settings NONE = new Settings(false, false, false, false);
        private static final String DISPLAY_CAPTCHA = "displayCaptcha";
        private static final String TRACK_USER = "trackUser";
        private static final String SAVABLE = "isFormSavable";
        private static final String START_DATE = "startDate";
        private static final String END_DATE = "endDate";
        private static final String MAX_SUBMISSIONS = "maxSubmissions";

        static Settings from(XmlNode node) {
            boolean constrained = isSet(node.attribute(START_DATE)) || isSet(node.attribute(END_DATE))
                    || (isSet(node.attribute(MAX_SUBMISSIONS)) && !"0".equals(node.attribute(MAX_SUBMISSIONS).trim()));
            return new Settings(flag(node, DISPLAY_CAPTCHA), flag(node, TRACK_USER), flag(node, SAVABLE), constrained);
        }

        private static boolean flag(XmlNode node, String property) {
            return Boolean.parseBoolean(node.attribute(property));
        }

        private static boolean isSet(String value) {
            return value != null && !value.isBlank();
        }
    }

    static FormsForm from(XmlNode node) {
        List<FormsStep> steps = new ArrayList<>(node.childrenOfType(FormsStep.TYPE).stream().map(FormsStep::from).toList());
        steps.sort(Comparator.comparingLong(FormsStep::number));
        List<FormsAction> actions = node.child(FormsAction.ACTIONS_NODE)
                .map(a -> a.children().stream().map(FormsAction::from).toList())
                .orElse(List.of());
        return new FormsForm(node.name(), node.uuid(), node.path(), node.attribute(BUILDING_LANG),
                node.i18n("jcr:title"), node.i18n(AFTER_SUBMISSION_TEXT), Settings.from(node), steps, actions);
    }

    /** Every field of every step, in order. */
    List<FormsField> fields() {
        return steps.stream().flatMap(s -> s.fields().stream()).toList();
    }

    /** The field with that {@code jcr:uuid}, or null. */
    FormsField fieldById(String fieldUuid) {
        return fields().stream().filter(f -> fieldUuid != null && fieldUuid.equals(f.uuid())).findFirst().orElse(null);
    }
}
