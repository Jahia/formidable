package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One submission of Forms, a {@code fcnt:result} node named by a random UUID under the split folders of
 * {@code results/<form>/submissions}.
 *
 * @param uuid the node name, the identity of the submission in Forms
 * @param formName the name of the {@code fcnt:formResults} the submission sits under
 * @param created {@code jcr:created}, the moment of the submission
 * @param origin the {@code origin} property: the referer, or the request URI
 * @param ipAddress {@code ip_address}, present when the form tracked its users, never imported
 * @param createdBy {@code jcr:createdBy}, {@code guest} unless the form tracked its users, never imported
 */
record FormsSubmission(String uuid, String formName, Instant created, String origin, String ipAddress,
                       String createdBy, List<FormsResultField> fields, String path) {

    static final String TYPE = "fcnt:result";
    private static final String CREATED_PROPERTY = "jcr:created";
    private static final String CREATED_BY = "jcr:createdBy";
    private static final String ORIGIN_PROPERTY = "origin";
    private static final String IP_ADDRESS = "ip_address";
    private static final String RESULTS_SEGMENT = "/results/";

    static FormsSubmission from(XmlNode node) {
        List<FormsResultField> fields = node.childrenOfType(FormsResultField.TYPE).stream()
                .map(FormsResultField::from)
                .toList();
        return new FormsSubmission(node.name(), formNameOf(node.path()), parseInstant(node.attribute(CREATED_PROPERTY)),
                node.attribute(ORIGIN_PROPERTY), node.attribute(IP_ADDRESS), node.attribute(CREATED_BY), fields, node.path());
    }

    /** The segment after {@code results/} in the path: {@code formFactory/results/contact-us/submissions/…}. */
    static String formNameOf(String path) {
        int at = path.indexOf(RESULTS_SEGMENT);
        if (at < 0) {
            return null;
        }
        String rest = path.substring(at + RESULTS_SEGMENT.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    static Instant parseInstant(String iso) {
        return iso == null ? null : OffsetDateTime.parse(iso).toInstant();
    }

    FormsResultField field(String fieldName) {
        return fields.stream().filter(f -> f.name().equals(fieldName)).findFirst().orElse(null);
    }
}
