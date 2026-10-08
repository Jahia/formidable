package org.jahia.modules.formidable.engine.config.formsimport;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;

import java.util.Map;

/**
 * The Forms import theme, read from {@code org.jahia.modules.formidable.formsImport.cfg}: whether the
 * Results page offers the import, and the largest export it takes.
 */
@Component(service = FormsImportConfigService.class, configurationPid = FormsImportConfigService.PID, immediate = true)
@Designate(ocd = FormsImportConfig.class)
public class FormsImportConfigService {

    public static final String PID = "org.jahia.modules.formidable.formsImport";
    private static final long DEFAULT_MAX_FILE_SIZE_MB = 200;

    private record Snapshot(boolean importButtonEnabled, long maxFileSizeMb) {
    }

    private final ThemeLifecycle<FormsImportConfig, Snapshot> lifecycle =
            new ThemeLifecycle<>(PID, FormsImportConfig.class, FormsImportConfigService::read);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
    }

    @Activate
    @Modified
    public void configure(FormsImportConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, config);
    }

    /** Reads the configuration into the snapshot the getters serve, no file behind it; public for the tests. */
    public void activate(FormsImportConfig config) {
        lifecycle.configure(null, config);
    }

    private static Snapshot read(FormsImportConfig config) {
        // a zero or negative bound is a mistake, not a way to accept any size
        long maxFileSizeMb = config.maxFileSizeMb() > 0 ? config.maxFileSizeMb() : DEFAULT_MAX_FILE_SIZE_MB;
        return new Snapshot(config.importButtonEnabled(), maxFileSizeMb);
    }

    public boolean isImportButtonEnabled() {
        return lifecycle.current().importButtonEnabled();
    }

    public long getMaxFileSizeMb() {
        return lifecycle.current().maxFileSizeMb();
    }

    public long getMaxFileSizeBytes() {
        return getMaxFileSizeMb() * 1024 * 1024;
    }
}
