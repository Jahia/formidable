package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.jahia.modules.formidable.engine.config.choiceoptions.ChoiceOptionsConfigService.OptionsSource;
import org.jahia.modules.formidable.engine.config.common.FactoryEntries;
import org.jahia.modules.formidable.engine.config.common.FactoryEntry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One options source file, as a component: DS creates one instance per configuration of the factory
 * {@value #FACTORY_PID} and hands it to {@link ChoiceOptionsConfigService}, which aggregates them. A file without
 * an id or an initializer is logged and contributes nothing.
 */
@Component(service = OptionsSourceComponent.class, configurationPid = OptionsSourceComponent.FACTORY_PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = OptionsSourceConfig.class, factory = true)
public class OptionsSourceComponent extends FactoryEntry {

    public static final String FACTORY_PID = "org.jahia.modules.formidable.choiceOptions.source";

    /** A source file's settings, in the order the file lists them. */
    static final List<String> SETTINGS = List.of("id", "label", "initializerKey", "param");

    private static final Logger log = LoggerFactory.getLogger(OptionsSourceComponent.class);

    private final AtomicReference<Optional<OptionsSource>> source = new AtomicReference<>(Optional.empty());

    @Activate
    @Modified
    public void configure(OptionsSourceConfig config, Map<String, Object> properties) {
        configured(config.id(), properties);
        String initializerKey = config.initializerKey().trim();
        if (id().isBlank() || initializerKey.isEmpty()) {
            log.warn("[OptionsSource] Skipping an options source configuration ({}): its id and its initializer are required.",
                    id().isBlank() ? "no id" : "id '" + id() + "'");
            source.set(Optional.empty());
            return;
        }
        String label = config.label().isBlank() ? id() : config.label().trim();
        source.set(Optional.of(new OptionsSource(id(), label, initializerKey, config.param().trim())));
    }

    /** The file a source is stored in — a convention for the reader: the id is the file's {@code id} setting. */
    public static String fileName(String id) {
        return FactoryEntries.fileName(FACTORY_PID, id);
    }

    /** The source this file describes; empty when it describes none usable. */
    public Optional<OptionsSource> source() {
        return source.get();
    }
}
