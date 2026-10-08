package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One action of a Forms form, a child of its {@code actions} node: {@code fcnt:saveToJcrAction},
 * {@code fcnt:sendEmailAction}, {@code fcnt:redirectToAPageAction}… with its option children.
 */
record FormsAction(String name, String type, Map<String, FormsOption> options) {

    static final String ACTIONS_NODE = "actions";
    static final String SAVE_TO_JCR = "fcnt:saveToJcrAction";
    static final String SEND_EMAIL = "fcnt:sendEmailAction";
    static final String SEND_EMAIL_TO_SUBMITTER = "fcnt:sendEmailToSubmitterAction";
    static final String REDIRECT_TO_PAGE = "fcnt:redirectToAPageAction";
    static final String REDIRECT_TO_URL = "fcnt:redirectToUrlAction";

    static FormsAction from(XmlNode node) {
        Map<String, FormsOption> options = new LinkedHashMap<>();
        for (XmlNode child : node.children()) {
            if (FormsOption.isOption(child)) {
                options.put(child.name(), FormsOption.from(child));
            }
        }
        return new FormsAction(node.name(), node.primaryType(), options);
    }

    boolean is(String actionType) {
        return actionType.equals(type);
    }

    FormsOption option(String optionName) {
        return options.get(optionName);
    }
}
