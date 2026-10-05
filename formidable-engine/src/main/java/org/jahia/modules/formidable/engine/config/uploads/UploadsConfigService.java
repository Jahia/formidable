package org.jahia.modules.formidable.engine.config.uploads;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.jahia.modules.formidable.engine.files.AllowedTypes;
import org.jahia.modules.formidable.engine.migration.RemovedIn;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The uploads theme read from {@code org.jahia.modules.formidable.uploads.cfg}: the bounds the multipart parser
 * and the pipeline's size guard apply, and the file types a file field may accept — each resolved to its MIME type
 * here, once, so that everything downstream compares MIME types ({@link AllowedTypes}).
 */
@Component(service = UploadsConfigService.class, configurationPid = UploadsConfigService.PID, immediate = true)
@Designate(ocd = UploadsConfig.class)
public class UploadsConfigService {

    public static final String PID = "org.jahia.modules.formidable.uploads";
    /** The name uploadAllowedTypes had until 0.5, carried from the single PID; still written by an old script. */
    @RemovedIn("0.6")
    static final String FORMER_ALLOWED_TYPES = "uploadAllowedMimeTypes";

    private record Snapshot(long maxFileSizeBytes, long maxRequestSizeBytes, int maxFileCount, Set<String> allowedTypes) {}

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
        warnOfTheFormerName(properties);
        lifecycle.configure(properties, config);
    }

    /** The former name still written by an old script; removed in 0.6 with the migration of the single PID. */
    @RemovedIn("0.6")
    private static void warnOfTheFormerName(Map<String, Object> properties) {
        if (properties != null && properties.containsKey(FORMER_ALLOWED_TYPES)) {
            log.warn("{} is no longer read: the setting is uploadAllowedTypes (extensions, MIME types or wildcards)",
                    FORMER_ALLOWED_TYPES);
        }
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
                allowedTypes(config.uploadAllowedTypes())
        );
        log.info("UploadsConfigService configured: maxFileSize={}MB, maxRequest={}MB, maxFileCount={}, allowedTypes={}",
                snapshot.maxFileSizeBytes() / 1_048_576,
                snapshot.maxRequestSizeBytes() / 1_048_576,
                snapshot.maxFileCount(),
                snapshot.allowedTypes().size());
        return snapshot;
    }

    public long getUploadMaxFileSizeBytes()    { return lifecycle.current().maxFileSizeBytes(); }
    public long getUploadMaxRequestSizeBytes() { return lifecycle.current().maxRequestSizeBytes(); }
    public int  getUploadMaxFileCount()        { return lifecycle.current().maxFileCount(); }
    /** The MIME types and wildcards a file field may accept; empty means no file is accepted. */
    public Set<String> getUploadAllowedTypes() { return lifecycle.current().allowedTypes(); }

    /**
     * The configured tokens as MIME types: an extension or an alias is resolved by Tika — the resolutions logged once,
     * so the administrator sees what an entry stands for —, a token that is no file type is dropped with a warning.
     */
    private static Set<String> allowedTypes(String configured) {
        Set<String> types = new LinkedHashSet<>();
        List<String> resolutions = new ArrayList<>();
        for (String token : ConfigurationValues.commaSeparated(configured)) {
            Optional<String> type = AllowedTypes.resolve(token);
            if (type.isEmpty()) {
                log.warn("uploadAllowedTypes: '{}' is neither a MIME type nor an extension Apache Tika knows; ignored", token);
                continue;
            }
            if (!type.get().equals(token.trim().toLowerCase(Locale.ROOT))) {
                resolutions.add(token.trim() + " = " + type.get());
            }
            types.add(type.get());
        }
        if (!resolutions.isEmpty()) {
            log.info("uploadAllowedTypes: read as {}", resolutions);
        }
        if (types.isEmpty()) {
            log.warn("uploadAllowedTypes allows no file type: every file field refuses every file. To accept any file, "
                    + "set {}", AllowedTypes.ANY_FILE);
        }
        return Collections.unmodifiableSet(types);
    }
}
