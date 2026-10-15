package org.jahia.modules.formidable.engine.config.formsimport;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The Forms import settings, read from {@code org.jahia.modules.formidable.formsImport.cfg}: whether the
 * Results page offers the import, and the largest export it takes.
 * <p>
 * A plain configuration, read as the framework hands it over: the file ships with the import and is new with
 * it, so it has no earlier single file to carry settings over from, and the completion of absent keys the
 * engine applies to its own theme files is not applied to it.
 */
@Component(service = FormsImportConfigService.class, configurationPid = FormsImportConfigService.PID, immediate = true)
@Designate(ocd = FormsImportConfig.class)
public class FormsImportConfigService {

    public static final String PID = "org.jahia.modules.formidable.formsImport";
    private static final long DEFAULT_MAX_FILE_SIZE_MB = 200;

    private record Snapshot(boolean importButtonEnabled, long maxFileSizeMb) {
    }

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(false, DEFAULT_MAX_FILE_SIZE_MB));

    @Activate
    @Modified
    public void activate(FormsImportConfig config) {
        // a zero or negative bound is a mistake, not a way to accept any size
        long maxFileSizeMb = config.maxFileSizeMb() > 0 ? config.maxFileSizeMb() : DEFAULT_MAX_FILE_SIZE_MB;
        snapshot.set(new Snapshot(config.importButtonEnabled(), maxFileSizeMb));
    }

    public boolean isImportButtonEnabled() {
        return snapshot.get().importButtonEnabled();
    }

    public long getMaxFileSizeMb() {
        return snapshot.get().maxFileSizeMb();
    }

    public long getMaxFileSizeBytes() {
        return getMaxFileSizeMb() * 1024 * 1024;
    }
}
