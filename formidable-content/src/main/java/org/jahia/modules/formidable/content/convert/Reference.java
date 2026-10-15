package org.jahia.modules.formidable.content.convert;

/**
 * A value that points to a node, by identifier: the page a form was submitted from, a stored file. Resolved to a JCR
 * reference at write time, in the session that writes. Immutable.
 *
 * @param identifier the node's UUID
 */
public record Reference(String identifier) {
}
