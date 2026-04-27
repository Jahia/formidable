package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Populates the accept choice list for fmdb:inputFile from the
 * upload.allowedMimeTypes configuration in org.jahia.modules.formidable.cfg.
 *
 * Labels are resolved by Jahia's renderer from the module resource bundle
 * using the convention key: fmdb_inputFile.accept.{mime/type}
 * (e.g. fmdb_inputFile.accept.image/jpeg=JPEG Image)
 * If no key is defined, the raw MIME type is displayed.
 *
 * Registered as: choicelist[formidableMimeTypes] in the CND.
 */
@Component(service = ChoiceListInitializer.class, property = {"name=formidableMimeTypes"})
public class FormidableMimeTypesInitializer implements ChoiceListInitializer {

    private FormidableConfigService configService;

    @Reference
    public void setConfigService(FormidableConfigService service) {
        this.configService = service;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        return configService.getUploadAllowedMimeTypes().stream()
                .sorted()
                .map(mime -> new ChoiceListValue(mime, mime))
                .collect(Collectors.toList());
    }
}
