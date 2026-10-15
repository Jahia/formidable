package org.jahia.modules.formidable.formsimport;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.Calendar;

/**
 * Writes a file node the way the engine stores an uploaded file: a {@code jnt:file} with its {@code jcr:content}
 * resource, the binary created from the stream by the session's value factory. A stream that fails to read
 * fails the write, with the cause — where {@code JCRNodeWrapper.uploadFile} logs the failure and saves a
 * resource without data, which the save then refuses for its missing {@code jcr:data}, the read error gone.
 * The import keeps its own copy of the engine's helper: the JCR API alone, nothing of the engine.
 */
final class ImportFiles {

    private static final String FILE_TYPE = "jnt:file";
    private static final String CONTENT_NODE = "jcr:content";
    private static final String RESOURCE_TYPE = "jnt:resource";
    private static final String DATA = "jcr:data";
    private static final String MIME_TYPE = "jcr:mimeType";
    private static final String LAST_MODIFIED = "jcr:lastModified";

    private ImportFiles() {
    }

    /**
     * Adds the file under the parent; the caller picks a free name and closes the stream.
     *
     * @throws RepositoryException when the node cannot be added, or when the stream cannot be read: the
     *                             {@code IOException} is then its cause
     */
    static JCRNodeWrapper add(JCRNodeWrapper parent, String name, InputStream data, String mimeType) throws RepositoryException {
        JCRNodeWrapper file = parent.addNode(name, FILE_TYPE);
        JCRNodeWrapper content = file.addNode(CONTENT_NODE, RESOURCE_TYPE);
        Binary binary = parent.getSession().getValueFactory().createBinary(data);
        try {
            content.setProperty(DATA, binary);
        } finally {
            binary.dispose();
        }
        content.setProperty(MIME_TYPE, mimeType);
        content.setProperty(LAST_MODIFIED, Calendar.getInstance());
        return file;
    }
}
