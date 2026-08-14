package org.jahia.test.modules.formidable.samples.choicelist;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sample choicelist initializer listing the child categories of a category-tree node.
 *
 * The initializer parameter targets the starting point, as a path relative to the
 * global category root /sites/systemsite/categories (for example: product/tv). Each
 * direct child category becomes one choice: the category name is the stored value,
 * its localized title the display label.
 *
 * Exists to exercise the Formidable options-source configuration with a
 * parameterized, project-contributed initializer, e.g.:
 *   optionsSources=tv|TV screens|fmdbSampleCategoryTree|product/tv
 */
@Component(service = ModuleChoiceListInitializer.class)
public class SampleCategoryTreeInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "fmdbSampleCategoryTree";
    private static final String CATEGORY_ROOT = "/sites/systemsite/categories";
    private static final String CATEGORY_TYPE = "jnt:category";
    private static final Logger logger = LoggerFactory.getLogger(SampleCategoryTreeInitializer.class);

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        String basePath = resolveBasePath(param);
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "default", locale, session -> {
                List<ChoiceListValue> choices = new ArrayList<>();
                JCRNodeWrapper base = session.getNode(basePath);
                NodeIterator children = base.getNodes();
                while (children.hasNext()) {
                    JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
                    if (child.isNodeType(CATEGORY_TYPE)) {
                        choices.add(new ChoiceListValue(child.getDisplayableName(), child.getName()));
                    }
                }

                return choices;
            });
        } catch (RepositoryException e) {
            // Propagated as a resolution failure so a sourced field renders its D10 error
            // instead of silently showing an empty list.
            throw new IllegalStateException("Cannot list categories under '" + basePath + "': " + e.getMessage(), e);
        }
    }

    private static String resolveBasePath(String param) {
        if (param == null || param.isBlank()) {
            return CATEGORY_ROOT;
        }
        String relative = param.strip().replaceAll("^/+", "").replaceAll("/+$", "");
        if (relative.contains("..")) {
            logger.warn("[SampleCategoryTreeInitializer] Ignoring suspicious parameter '{}', using the category root.", param);
            return CATEGORY_ROOT;
        }
        return CATEGORY_ROOT + "/" + relative;
    }

    @Override
    public void setKey(String key) {
        // Jahia injects the service key on registration; this initializer uses a fixed key.
    }

    @Override
    public String getKey() {
        return KEY;
    }
}
