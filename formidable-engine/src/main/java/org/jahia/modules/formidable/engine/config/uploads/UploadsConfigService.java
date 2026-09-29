package org.jahia.modules.formidable.engine.config.uploads;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * The uploads theme read from {@code org.jahia.modules.formidable.uploads.cfg}: the bounds the multipart parser
 * and the pipeline's size guard apply, and the MIME types a file field accepts when it declares none.
 */
@Component(service = UploadsConfigService.class, configurationPid = UploadsConfigService.PID, immediate = true)
@Designate(ocd = UploadsConfig.class)
public class UploadsConfigService {

    public static final String PID = "org.jahia.modules.formidable.uploads";

    private record Snapshot(long maxFileSizeBytes, long maxRequestSizeBytes, int maxFileCount, Set<String> allowedMimeTypes) {}

    private static final Logger log = LoggerFactory.getLogger(UploadsConfigService.class);

    private final ThemeLifecycle<UploadsConfig, Snapshot> lifecycle = new ThemeLifecycle<>(PID, UploadsConfig.class, UploadsConfigService::read);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
    }

    @Activate
    @Modified
    public void configure(UploadsConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, config);
    }

    /** Reads the configuration into the snapshot the getters serve, no file behind it; public for the tests. */
    public void activate(UploadsConfig config) {
        lifecycle.configure(null, config);
    }

    private static Snapshot read(UploadsConfig config) {
        // A zero or negative limit is a configuration mistake, not a way to disable the cap (-1 would also break
        // the early Content-Length guard, which compares against this value).
        Snapshot snapshot = new Snapshot(
                ConfigurationValues.positiveLong("uploadMaxFileSizeBytes", config.uploadMaxFileSizeBytes(), UploadsConfig.DEFAULT_UPLOAD_MAX_FILE_SIZE_BYTES),
                ConfigurationValues.positiveLong("uploadMaxRequestSizeBytes", config.uploadMaxRequestSizeBytes(), UploadsConfig.DEFAULT_UPLOAD_MAX_REQUEST_SIZE_BYTES),
                (int) ConfigurationValues.positiveLong("uploadMaxFileCount", config.uploadMaxFileCount(), UploadsConfig.DEFAULT_UPLOAD_MAX_FILE_COUNT),
                Set.copyOf(ConfigurationValues.commaSeparated(config.uploadAllowedMimeTypes()))
        );
        log.info("UploadsConfigService configured: maxFileSize={}MB, maxRequest={}MB, maxFileCount={}, allowedTypes={}",
                snapshot.maxFileSizeBytes() / 1_048_576,
                snapshot.maxRequestSizeBytes() / 1_048_576,
                snapshot.maxFileCount(),
                snapshot.allowedMimeTypes().size());
        return snapshot;
    }

    public long getUploadMaxFileSizeBytes()    { return lifecycle.current().maxFileSizeBytes(); }
    public long getUploadMaxRequestSizeBytes() { return lifecycle.current().maxRequestSizeBytes(); }
    public int  getUploadMaxFileCount()        { return lifecycle.current().maxFileCount(); }
    public Set<String> getUploadAllowedMimeTypes() { return lifecycle.current().allowedMimeTypes(); }
}
