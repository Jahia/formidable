package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.List;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.MANUAL_OPTIONS_MIXIN;

/**
 * The manual options a choice field must RENDER in one language: the site default
 * language's values, order and default selections, carrying that language's own
 * labels.
 *
 * A view cannot simply render the list stored in the language it renders. Values are
 * one identity set owned by the default language, and the submission validation reads
 * the allowed values from there — while publication is per language, so live can hold
 * a translation at an older generation than the default language. Rendering the
 * translation verbatim then offers the visitor values the server would reject as
 * forged. Reading the identity here and the label there makes the two sides agree at
 * every publication state.
 *
 * Exposed as an OSGi service because the callers are the GraalVM JS server views,
 * which reach it through {@code server.osgi.getService} — the same bridge
 * FormidableOptionsSourceService uses, and the same JSON storage format out.
 */
@Component(service = ManualOptionsDisplayService.class, immediate = true)
public class ManualOptionsDisplayService {

    /**
     * @param fieldNode   the choice field node
     * @param languageTag the language being rendered
     * @return the entries to render, or null when the stored list already IS the
     *         identity — a field rendered in the default language, one whose options
     *         are not manual, or one with no default-language list to align on (a
     *         field authored in another language only). The caller then renders what
     *         it read from the node.
     */
    public String[] forDisplay(JCRNodeWrapper fieldNode, String languageTag) throws RepositoryException {
        if (languageTag == null || !fieldNode.isNodeType(MANUAL_OPTIONS_MIXIN)) {
            return null;
        }

        JCRSiteNode site = fieldNode.getResolveSite();
        String masterLanguage = site != null ? site.getDefaultLanguage() : null;
        if (masterLanguage == null || masterLanguage.equals(languageTag)) {
            return null;
        }

        Node master = ManualOptionEntries.findTranslation(fieldNode, masterLanguage);
        if (master == null) {
            return null;
        }

        List<String> masterOptions = ManualOptionEntries.readOptions(master);
        if (masterOptions.isEmpty()) {
            return null;
        }

        Node own = ManualOptionEntries.findTranslation(fieldNode, languageTag);
        List<String> ownOptions = own != null ? ManualOptionEntries.readOptions(own) : List.of();
        return ManualOptionEntries.alignForDisplay(masterOptions, ownOptions).toArray(new String[0]);
    }
}
