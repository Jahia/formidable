package org.jahia.test.modules.formidable.quarantine.chain;

import java.util.Objects;

import org.jahia.test.modules.formidable.quarantine.check.CheckSettings;
import org.jahia.test.modules.formidable.quarantine.check.Qualifier;

/**
 * One check of the list, with the qualifier the wiring found for its node type.
 *
 * @param settings  the settings read from the check node
 * @param qualifier the service of the node type, or null when no service of that type is registered
 */
public record ChainCheck(CheckSettings settings, Qualifier qualifier) {

    public ChainCheck {
        Objects.requireNonNull(settings, "settings");
    }

    /** @return the name of the check node, under which its outcomes are recorded */
    public String name() {
        return settings.name();
    }

    /** @return true when a qualifier serves the node type of the check */
    public boolean isResolved() {
        return qualifier != null && settings.nodeType().equals(qualifier.getNodeType());
    }
}
