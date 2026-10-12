package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One prefill node of a Forms field, under its {@code prefills} child: a node of a {@code fcnt:*Prefill} type
 * whose {@code dataKey} says what feeds the field, with the two author behaviours of Forms as option children.
 * The jExperience bridge of Forms ({@code jexperience-forms-bridge}) ships {@code fcnt:mfffPrefill}, which
 * prefills the field from a property of the visitor profile and maps the submitted value back to it, and
 * {@code fcnt:mfffMappingOnly}, which maps without prefilling; their {@code dataKey} is the JSON of the
 * profile property, whose {@code itemId} is its identifier ({@code firstName}).
 *
 * @param type the primary type of the node
 * @param profileProperty the identifier of the visitor profile property, for the two jExperience types; null otherwise
 * @param hideAfterPrefill whether Forms hid the field once prefilled
 * @param readOnlyAfterPrefill whether Forms made the field read-only once prefilled
 */
record FormsPrefill(String type, String profileProperty, boolean hideAfterPrefill, boolean readOnlyAfterPrefill) {

    static final String PROFILE_PREFILL_TYPE = "fcnt:mfffPrefill";
    static final String PROFILE_MAPPING_ONLY_TYPE = "fcnt:mfffMappingOnly";
    private static final String DATA_KEY = "dataKey";
    private static final String ITEM_ID = "itemId";
    private static final String HIDE_AFTER = "hideAfterPrefill";
    private static final String READ_ONLY_AFTER = "makeReadOnlyAfterPrefill";

    static FormsPrefill from(XmlNode node) {
        String type = node.primaryType();
        String property = null;
        if (PROFILE_PREFILL_TYPE.equals(type) || PROFILE_MAPPING_ONLY_TYPE.equals(type)) {
            property = profilePropertyOf(node.attribute(DATA_KEY));
        }
        return new FormsPrefill(type, property, flag(node, HIDE_AFTER), flag(node, READ_ONLY_AFTER));
    }

    /** The identifier of the profile property a {@code dataKey} names; null when the JSON holds none. */
    static String profilePropertyOf(String dataKey) {
        if (dataKey == null || dataKey.isBlank()) {
            return null;
        }
        try {
            String id = new JSONObject(dataKey).optString(ITEM_ID, "");
            return id.isBlank() ? null : id;
        } catch (JSONException e) {
            return null;
        }
    }

    private static boolean flag(XmlNode node, String optionName) {
        for (XmlNode child : node.children()) {
            if (optionName.equals(child.name()) && FormsOption.isOption(child)) {
                return Boolean.parseBoolean(FormsOption.from(child).in(null));
            }
        }
        return false;
    }

    /** A prefill from the visitor profile, mapped to a property the bridge knows: what Formidable carries over. */
    boolean isProfileMapping() {
        return profileProperty != null;
    }

    /** Whether the field was prefilled from the profile, or only mapped to it for the sending. */
    boolean prefills() {
        return PROFILE_PREFILL_TYPE.equals(type);
    }
}
