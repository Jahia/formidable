package org.jahia.modules.formidable.engine.imports.jahiaforms;

/**
 * One file a visitor uploaded, a {@code jnt:file} child of a result field. Its binary is not in the XML:
 * the zip holds it under the path of the node, which {@link FormsExportZip} resolves.
 *
 * @param path the path of the node in the export, {@code formFactory/results/.../<uuid>/<field>/<file>}
 */
record FormsFile(String name, String mimeType, String path) {

    static final String TYPE = "jnt:file";
    private static final String CONTENT_NODE = "jcr:content";
    private static final String MIME_TYPE = "jcr:mimeType";

    static FormsFile from(XmlNode node) {
        String mimeType = node.child(CONTENT_NODE).map(c -> c.attribute(MIME_TYPE)).orElse(null);
        return new FormsFile(node.name(), mimeType, node.path());
    }
}
