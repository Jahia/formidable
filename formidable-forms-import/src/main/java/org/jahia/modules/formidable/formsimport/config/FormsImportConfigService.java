package org.jahia.modules.formidable.formsimport.config;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The Forms import settings, read from {@code org.jahia.modules.formidable.formsImport.cfg}: the largest
 * export the Results page takes. Whether the page offers the import is no setting: the module is enabled on
 * the site, or not, and the endpoint reads that off the site.
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

    private final AtomicLong maxFileSizeMb = new AtomicLong(DEFAULT_MAX_FILE_SIZE_MB);

    @Activate
    @Modified
    public void activate(FormsImportConfig config) {
        // a zero or negative bound is a mistake, not a way to accept any size
        maxFileSizeMb.set(config.maxFileSizeMb() > 0 ? config.maxFileSizeMb() : DEFAULT_MAX_FILE_SIZE_MB);
    }

    public long getMaxFileSizeMb() {
        return maxFileSizeMb.get();
    }

    public long getMaxFileSizeBytes() {
        return getMaxFileSizeMb() * 1024 * 1024;
    }
}
