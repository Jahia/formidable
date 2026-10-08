package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One node of a Jahia document-view export, decoded: the element name is the node name, the attributes
 * are its properties, the child elements its child nodes. {@link #path()} is the path from the root of
 * the export, {@code formFactory/results/contact-us}, which is how the export writes its references.
 */
final class XmlNode {

    static final String PRIMARY_TYPE = "jcr:primaryType";
    static final String UUID_PROPERTY = "jcr:uuid";
    private static final String TRANSLATION_PREFIX = "j:translation_";

    private final String name;
    private final String path;
    private final Map<String, String> attributes;
    private final List<XmlNode> children = new ArrayList<>();

    XmlNode(String name, String path, Map<String, String> attributes) {
        this.name = name;
        this.path = path;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    String name() {
        return name;
    }

    String path() {
        return path;
    }

    String primaryType() {
        return attributes.get(PRIMARY_TYPE);
    }

    String uuid() {
        return attributes.get(UUID_PROPERTY);
    }

    boolean isOfType(String primaryType) {
        return primaryType.equals(primaryType());
    }

    /** A single-valued property, decoded, or null. */
    String attribute(String name) {
        String raw = attributes.get(name);
        return raw == null ? null : Iso9075.decode(raw);
    }

    /** A multi-valued property: the values are space-separated in the attribute, each one encoded. */
    List<String> values(String name) {
        String raw = attributes.get(name);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(raw.split(" ")).map(Iso9075::decode).toList();
    }

    boolean has(String name) {
        return attributes.containsKey(name);
    }

    List<XmlNode> children() {
        return Collections.unmodifiableList(children);
    }

    void add(XmlNode child) {
        children.add(child);
    }

    Optional<XmlNode> child(String childName) {
        return children.stream().filter(c -> c.name.equals(childName)).findFirst();
    }

    List<XmlNode> childrenOfType(String primaryType) {
        return children.stream().filter(c -> c.isOfType(primaryType)).toList();
    }

    /**
     * An i18n property, from the {@code j:translation_<lang>} children: language to decoded value, for
     * the languages that hold the property.
     */
    Map<String, String> i18n(String property) {
        Map<String, String> byLanguage = new LinkedHashMap<>();
        for (XmlNode child : children) {
            if (child.name.startsWith(TRANSLATION_PREFIX) && child.has(property)) {
                byLanguage.put(child.name.substring(TRANSLATION_PREFIX.length()), child.attribute(property));
            }
        }
        return byLanguage;
    }

    @Override
    public String toString() {
        return path + " [" + primaryType() + "]";
    }
}
