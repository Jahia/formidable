package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Populates the accept choice list for fmdb:inputFile from the
 * uploadAllowedMimeTypes configuration in org.jahia.modules.formidable.cfg.
 *
 * Labels are resolved by Jahia's resourceBundle choicelist initializer from the
 * resource bundle of the module declaring the property definition.
 * For fmdb:inputFile.accept, the expected keys are:
 * fmdb_inputFile.accept.{mime/type}
 *
 * Registered as: choicelist[formidableMimeTypes] in the CND.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormidableMimeTypesInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "formidableMimeTypes";

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

    @Override
    public void setKey(String key) {
        // Jahia injects the service key on registration; this initializer uses a fixed key.
    }

    @Override
    public String getKey() {
        return KEY;
    }
}
