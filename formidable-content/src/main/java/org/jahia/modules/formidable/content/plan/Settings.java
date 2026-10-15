package org.jahia.modules.formidable.content.plan;

import org.jahia.modules.formidable.content.mapping.Mappings;
import org.jahia.modules.formidable.content.mapping.PairRule;

import java.util.List;

/**
 * The settings of one Create content action node, parsed: the mapping lines split, the pair rules read. What the
 * author typed, before it is checked against the node types ({@link PlanBuilder}). Immutable.
 *
 * @param contentType          the node type to create
 * @param groupPrefix          the prefix of the group that names the submitter's subfolder, or {@code null}
 * @param titleField           the field that gives {@code jcr:title}, or {@code null}
 * @param fieldMappings        {@code formField=property}
 * @param fileMappings         {@code fileField=property}
 * @param fileMixin            a mixin added to every stored file, or {@code null}
 * @param fileProperties       {@code formField=fileProperty}
 * @param fixedValues          {@code property=value}
 * @param submitterProperty    the property that receives the user name, or {@code null}
 * @param organizationProperty the property that receives the user's organization, or {@code null}
 * @param pageProperty         the property that receives a reference to the page of the form, or {@code null}
 * @param pairs                the companion-file rules
 * @param publish              whether to publish what is created
 */
public record Settings(
        String contentType,
        String groupPrefix,
        String titleField,
        List<Mappings.Entry> fieldMappings,
        List<Mappings.Entry> fileMappings,
        String fileMixin,
        List<Mappings.Entry> fileProperties,
        List<Mappings.Entry> fixedValues,
        String submitterProperty,
        String organizationProperty,
        String pageProperty,
        List<PairRule> pairs,
        boolean publish
) {
}
