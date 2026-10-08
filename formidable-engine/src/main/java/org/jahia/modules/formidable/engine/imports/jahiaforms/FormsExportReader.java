package org.jahia.modules.formidable.engine.imports.jahiaforms;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Reads a Forms export (docs/architecture/forms-import.md, "The export file"): first its structure,
 * the forms and the results entries with their labels, small enough to hold; then its submissions,
 * streamed one by one, because an export of a busy site is large and is read twice, by the dry run and
 * by the import.
 */
public final class FormsExportReader implements java.io.Closeable {

    private final FormsExportZip zip;

    FormsExportReader(FormsExportZip zip) {
        this.zip = zip;
    }

    /** Opens an export file; the caller closes the reader when done with it. */
    public static FormsExportReader open(Path exportFile) throws IOException {
        return new FormsExportReader(new FormsExportZip(exportFile));
    }

    /** The size of an uploaded file in the export, or -1 when the zip does not hold it. */
    public long binarySize(FormsFile file) {
        return zip.binarySize(file.path(), file.name());
    }

    public void close() throws IOException {
        zip.close();
    }

    /**
     * The forms and the results entries of the export. Refused when the file is not a Jahia export of a
     * {@code formFactory} node, or when it holds no results entry: the export of a single form, or an
     * export taken without live content.
     */
    public FormsExport readStructure() throws IOException, FormsExportException {
        XmlNode root;
        try (InputStream xml = zip.openRepositoryXml()) {
            // the submissions folders are not even built: one split folder per submission on a busy site
            root = XmlTreeReader.readTree(xml, FormsResults.SUBMISSIONS_TYPE);
        } catch (XMLStreamException e) {
            throw new FormsExportException("The repository.xml of the file cannot be parsed: " + e.getMessage(), e);
        }
        if (root == null || (root.child(FormsExport.FORMS_NODE).isEmpty() && root.child(FormsExport.RESULTS_NODE).isEmpty())) {
            throw new FormsExportException("The file is not the export of a formFactory node: it holds neither "
                    + "forms nor results. " + FormsExportException.PROCEDURE);
        }
        FormsExport export = FormsExport.from(root);
        if (export.results().isEmpty()) {
            throw new FormsExportException("The file holds forms but no results: it was taken without the live "
                    + "content, or it is the export of a single form. " + FormsExportException.PROCEDURE);
        }
        return export;
    }

    /** Streams every {@code fcnt:result} of the export, wherever it sits under the split folders. */
    public void readSubmissions(Consumer<FormsSubmission> sink) throws IOException, FormsExportException {
        try (InputStream xml = zip.openRepositoryXml()) {
            XmlTreeReader.readSubtrees(xml, FormsSubmission.TYPE, node -> sink.accept(FormsSubmission.from(node)));
        } catch (XMLStreamException e) {
            throw new FormsExportException("The repository.xml of the file cannot be parsed: " + e.getMessage(), e);
        }
    }

    /** The binary of an uploaded file, or null when the zip does not hold it. The caller closes it. */
    public InputStream openBinary(FormsFile file) throws IOException {
        return zip.openBinary(file.path(), file.name());
    }
}
