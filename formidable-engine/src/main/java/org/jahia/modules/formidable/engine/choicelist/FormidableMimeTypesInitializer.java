package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.modules.formidable.engine.files.FileTypeService;
import org.jahia.utils.i18n.Messages;
import org.jahia.utils.i18n.ResourceBundles;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Populates the accept choice list for fmdb:inputFile from the
 * uploadAllowedMimeTypes configuration in org.jahia.modules.formidable.uploads.cfg.
 *
 * Each type is labelled with the wording of the resource bundle of the module declaring the property, where it
 * has one — {@code fmdb_inputFile.accept.<mime/type>}, looked up as Jahia's own resourceBundle initializer does —
 * and otherwise from Apache Tika's registry ({@link FileTypeService#label}: "PDF (.pdf)"), so a type the
 * administrator adds needs no bundle entry. Jahia's resourceBundle initializer is not chained after this one: it
 * builds its key from the current label, which is no longer the type.
 *
 * Registered as: choicelist[formidableMimeTypes] in the CND.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormidableMimeTypesInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "formidableMimeTypes";

    private UploadsConfigService configService;
    private FileTypeService fileTypes;

    @Reference
    public void setConfigService(UploadsConfigService service) {
        this.configService = service;
    }

    @Reference
    public void setFileTypes(FileTypeService fileTypes) {
        this.fileTypes = fileTypes;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        return configService.getUploadAllowedMimeTypes().stream()
                .sorted()
                .map(mime -> new ChoiceListValue(label(epd, mime, locale), mime))
                .toList();
    }

    /** The bundle's wording of the type, else the label Tika gives it. */
    private String label(ExtendedPropertyDefinition epd, String mime, Locale locale) {
        String fallback = fileTypes.label(mime);
        try {
            JahiaTemplatesPackage module = epd.getDeclaringNodeType().getTemplatePackage();
            ResourceBundle bundle = ResourceBundles.get(module, locale);
            return Messages.get(bundle, epd.getResourceBundleKey() + "." + mime, fallback);
        } catch (RuntimeException e) {
            return fallback;
        }
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
