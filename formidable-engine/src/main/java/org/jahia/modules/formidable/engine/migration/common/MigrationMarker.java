package org.jahia.modules.formidable.engine.migration.common;

import org.jahia.modules.formidable.engine.migration.RemovedIn;

/**
 * The one-shot mixins the 0.4 content migrations stamp on the content they healed, so that they
 * never touch it twice. They are engine-internal on purpose and stay out of the exported api
 * package: each says only that this module has already been here, and the declarations outlive
 * the migrations that write them — kept so that content still carrying one stays valid, not
 * because anything outside reads them (see docs/administration/upgrade-notes.md).
 * <p>
 * They live here rather than in each reader because the choice-options marker outlived its
 * migration: the display service and the language sync read it on every save.
 *
 * <p>Lifecycle: removed in 0.6 with both waves of startup migrations (0.5.x becomes the minimum upgrade source) —
 * see docs/administration/upgrade-notes.md, "Startup migrations".
 */
@RemovedIn("0.6")
public final class MigrationMarker {

    /** Stamped on a site whose formidable-elements activation was healed. */
    public static final String ELEMENTS_REACTIVATED = "fmdbmix:elementsReactivated";

    /** Stamped on a choice field whose options came from 0.3 and have not realigned yet. */
    public static final String MIGRATED_CHOICE_OPTIONS = "fmdbmix:migratedChoiceOptions";

    private MigrationMarker() {
    }
}
