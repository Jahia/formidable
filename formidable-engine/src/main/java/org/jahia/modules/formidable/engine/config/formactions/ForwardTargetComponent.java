package org.jahia.modules.formidable.engine.config.formactions;

import org.jahia.modules.formidable.engine.config.common.EndpointRule;
import org.jahia.modules.formidable.engine.config.common.FactoryEntries;
import org.jahia.modules.formidable.engine.config.common.FactoryEntry;
import org.jahia.modules.formidable.engine.config.formactions.FormActionsConfigService.ForwardTarget;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One forward target file, as a component: DS creates one instance per configuration of the factory
 * {@value #FACTORY_PID} and hands it to {@link FormActionsConfigService}, which aggregates them. A file that does
 * not describe a usable target is logged and contributes nothing.
 */
@Component(service = ForwardTargetComponent.class, configurationPid = ForwardTargetComponent.FACTORY_PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = ForwardTargetConfig.class, factory = true)
public class ForwardTargetComponent extends FactoryEntry {

    public static final String FACTORY_PID = "org.jahia.modules.formidable.formActions.target";

    /** A target file's settings, in the order the file lists them. */
    static final List<String> SETTINGS = List.of("id", "label", "url", "development");

    private static final Logger log = LoggerFactory.getLogger(ForwardTargetComponent.class);

    private final AtomicReference<Optional<ForwardTarget>> target = new AtomicReference<>(Optional.empty());
    private volatile boolean development;

    @Activate
    @Modified
    public void configure(ForwardTargetConfig config, Map<String, Object> properties) {
        configured(config.id(), properties);
        development = config.development();
        target.set(target(id(), config.label(), config.url(), development));
    }

    /**
     * The target these settings describe, or empty with a warning naming the id and the reason: no id or no URL, a
     * malformed URL, or one the endpoint rule refuses (HTTPS with a host and no embedded credentials; plain HTTP on
     * localhost or host.docker.internal for a development target).
     */
    static Optional<ForwardTarget> target(String id, String label, String url, boolean development) {
        if (id == null || id.isBlank() || url == null || url.isBlank()) {
            log.warn("[ForwardTarget] Skipping a forward target configuration ({}): its id and its URL are required.",
                    id == null || id.isBlank() ? "no id" : "id '" + id + "'");
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            log.warn("[ForwardTarget] Skipping forward target '{}': malformed URL '{}'", id, url);
            return Optional.empty();
        }
        String reason = EndpointRule.unsupportedReason(uri, development);
        if (reason != null) {
            log.warn("[ForwardTarget] Skipping {} forward target '{}': {}", development ? "development" : "standard", id, reason);
            return Optional.empty();
        }
        return Optional.of(new ForwardTarget(id, label == null || label.isBlank() ? id : label.trim(), uri, development));
    }

    /** The file a target is stored in — a convention for the reader: the id is the file's {@code id} setting. */
    public static String fileName(String id) {
        return FactoryEntries.fileName(FACTORY_PID, id);
    }

    /** The target this file describes; empty when it describes none usable. */
    public Optional<ForwardTarget> target() {
        return target.get();
    }

    /** Whether the file declares a development target, honoured only behind the configuration's switch. */
    public boolean development() {
        return development;
    }
}
