package org.jahia.modules.formidable.content.convert;

import javax.jcr.PropertyType;

/**
 * What the node type says about one property, reduced to what the conversion and the write need. Immutable.
 *
 * @param name     the property name as the definition spells it ({@code ctmd:title}, {@code submissionStatus})
 * @param type     a {@link PropertyType} constant
 * @param multiple whether the property holds several values
 * @param i18n     whether the value is translated (written once per language of the site)
 */
public record PropertyShape(String name, int type, boolean multiple, boolean i18n) {

    /** @return whether the property holds references to nodes ({@code reference} or {@code weakreference}) */
    public boolean isReference() {
        return type == PropertyType.REFERENCE || type == PropertyType.WEAKREFERENCE;
    }

    /** @return whether a reference of this property is weak */
    public boolean isWeak() {
        return type == PropertyType.WEAKREFERENCE;
    }
}
