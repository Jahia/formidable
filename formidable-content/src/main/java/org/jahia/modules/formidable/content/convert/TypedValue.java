package org.jahia.modules.formidable.content.convert;

import java.util.List;

/**
 * The value or values of one property, already converted to what its definition holds: {@link String},
 * {@link Long}, {@link Double}, {@link java.math.BigDecimal}, {@link Boolean}, {@link java.util.Calendar} or
 * {@link Reference}. Immutable.
 *
 * @param shape  the property
 * @param values one value, or the values of a multiple property, never empty
 */
public record TypedValue(PropertyShape shape, List<Object> values) {

    public TypedValue {
        values = List.copyOf(values);
    }
}
