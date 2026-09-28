package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.modules.formidable.engine.files.FileTypeService;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Populates the accept choice list for fmdb:inputFile from the
 * uploadAllowedMimeTypes configuration in org.jahia.modules.formidable.uploads.cfg.
 *
 * Each type is labelled from Apache Tika's registry ({@link FileTypeService#label}: "PDF (.pdf)"), so a type the
 * administrator adds needs no bundle entry. Jahia's resourceBundle initializer, chained after this one in the
 * CND, still replaces that label with the module's own wording where its bundle has one:
 * fmdb_inputFile.accept.{mime/type}.
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
                .map(mime -> new ChoiceListValue(fileTypes.label(mime), mime))
                .toList();
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
