package org.jahia.modules.formidable.engine.choicelist;

import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Value;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Populates the targetId choice list for fmdb:forwardAction from the
 * forward_targets configuration in org.jahia.modules.formidable.cfg.
 *
 * Each entry exposes the target id as the stored JCR value and the
 * operator-defined label as the display name shown in the CMS editor.
 *
 * Registered as: choicelist[formidableForwardTargets] in the CND.
 */
@Component(service = ChoiceListInitializer.class, property = {"name=formidableForwardTargets"})
public class FormidableForwardTargetsInitializer implements ChoiceListInitializer {

    private FormidableConfigService configService;

    @Reference
    public void setConfigService(FormidableConfigService service) {
        this.configService = service;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        return configService.getForwardTargets().stream()
                .map(target -> {
                    try {
                        Value jcrValue = ValueFactoryImpl.getInstance().createValue(target.id());
                        return new ChoiceListValue(target.label(), null, jcrValue);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(cv -> cv != null)
                .collect(Collectors.toList());
    }
}

