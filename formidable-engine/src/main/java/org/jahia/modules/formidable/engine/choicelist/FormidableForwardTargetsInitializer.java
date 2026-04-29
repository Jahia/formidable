package org.jahia.modules.formidable.engine.choicelist;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Populates the targetId choice list for fmdb:forwardAction from the
 * configured forward target registries in org.jahia.modules.formidable.cfg.
 *
 * Each entry exposes the target id as the stored JCR value and the
 * operator-defined label as the display name shown in the CMS editor.
 *
 * Registered as: choicelist[formidableForwardTargets] in the CND.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class FormidableForwardTargetsInitializer implements ModuleChoiceListInitializer {

    private static final String KEY = "formidableForwardTargets";
    private static final Logger log = LoggerFactory.getLogger(FormidableForwardTargetsInitializer.class);

    private FormidableConfigService configService;

    @Reference
    public void setConfigService(FormidableConfigService service) {
        this.configService = service;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param,
            List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        Collection<FormidableConfigService.ForwardTarget> targets = configService.getForwardTargets();
        if (targets.isEmpty()) {
            log.warn("[FormidableForwardTargetsInitializer] No forward targets are configured. "
                    + "The choicelist '{}' for property '{}' will be empty.", KEY, epd.getName());
        }
        return targets.stream()
                .map(target -> new ChoiceListValue(target.label(), target.id()))
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
