import type {EditorLogicSource, LogicSourceRefChoices} from '@jahia/formidable-library/logic-sources';

/** The id every rule on the visitor's jExperience profile stores as its `sourceType`. */
export const PROFILE_SOURCE_ID = 'jexperience:profile';

// jCustomer profile property ids: one top-level name, read as a flat key, so no path syntax.
const PROFILE_PROPERTY_PATTERN = /^[\w-]+$/;

/**
 * The profile properties a rule may read on the site of the edited field (ProfilePropertiesQueryExtension).
 * Sent on its own, so that a failure never reaches the engine's queries: the rule row then keeps its text
 * input.
 */
export const PROFILE_PROPERTIES_QUERY = `
    query FormidableJExperienceProfileProperties($path: String!) {
        formidableJExperienceProfileProperties(path: $path) {
            available
            properties {
                name
                label
            }
        }
    }
`;

interface ProfilePropertiesData {
    formidableJExperienceProfileProperties?: {available?: boolean; properties?: {name?: string; label?: string}[]} | null;
}

/** The answer of PROFILE_PROPERTIES_QUERY as dropdown entries; unavailable when jCustomer could not be asked. */
export const readProfileProperties = (data: unknown): LogicSourceRefChoices => {
    const answer = (data as ProfilePropertiesData | null | undefined)?.formidableJExperienceProfileProperties;
    if (!answer?.available) {
        return {available: false, choices: []};
    }

    return {
        available: true,
        choices: (answer.properties ?? []).flatMap(({name, label}) => (name ? [{value: name, label: label || name}] : []))
    };
};

/**
 * The editor half of the source: a property of the visitor's jExperience profile. Its page half is
 * `logicSource` in src/main/resources/javascript/formidable-jxp.js, with the same id and config key.
 */
export const profileSource: EditorLogicSource = {
    configKey: 'property',
    labelKey: 'formidable-jexperience-engine:conditionalLogic.profile.label',
    configLabelKey: 'formidable-jexperience-engine:conditionalLogic.profile.configLabel',
    configPlaceholderKey: 'formidable-jexperience-engine:conditionalLogic.profile.configPlaceholder',
    isValidRef: ref => PROFILE_PROPERTY_PATTERN.test(ref.trim()),
    operators: ['equals', 'notEquals', 'contains', 'exists', 'notExists'],
    // the page's own gate (JExperienceSite.tracked): jExperience enabled on the site itself. The
    // script that reads the tracker comes with a global render filter, enabled on no site.
    requiredModules: ['jexperience'],
    refChoices: {query: PROFILE_PROPERTIES_QUERY, read: readProfileProperties}
};
