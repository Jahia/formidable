package org.jahia.modules.formidable.engine.migration;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.observation.JahiaEventListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

/**
 * One-shot content migration for the fields marked sensitive by a 0.5.0 development build (#365). The
 * author's "keep this field's value out of the visitor profile" was {@code jExperienceSensitive}, on
 * {@code fmdbmix:jExperienceSensitiveField} — a mixin of formidable-jexperience-engine attached to the
 * engine's marker with {@code extends} and kept always activated, which made the editor add it to every
 * mappable field on save, a write a translator's role cannot do. The flag is {@code profileSensitive} on
 * the engine's own {@code fmdbmix:profileMappableField} since then: every mappable field has it, nothing
 * to add. This migration copies the value under the new name and drops the retired mixin, in BOTH
 * workspaces — the jExperience module reads the flag in live as well as in default, so a published
 * sensitive field must come out of this still sensitive in live, without a republication. Where the new
 * name already holds a value, the current one wins and the retired property goes with its mixin.
 *
 * <p>The engine names a type of another module here, once, to retire it: the mixin stays declared in
 * formidable-jexperience-engine until 0.6 (hidden, no {@code extends}), so that a node still carrying it
 * deploys, and the two names leave together. No released version holds such fields (0.4.0 has no
 * jExperience module), so this serves the instances that ran a development build.
 *
 * <p>Runs at module activation on BOTH workspaces (the live pass through {@link MigrationSessions}) and
 * again on the redeploy of formidable-elements — whose field types must include the new property before it
 * can be written: a type keeps the supertypes it resolved at its own registration, see
 * {@link RedundantMixinMigration} — and of formidable-jexperience-engine, whose mixin must be registered
 * for the removal. A field whose type does not define the property yet, or whose retired mixin is not
 * registered, waits for that redeploy. Keyed on content state: a no-op once no field lists the mixin.
 *
 * <p>Lifecycle: startup migration introduced in 0.5.0, to be removed in 0.6 — see
 * docs/administration/upgrade-notes.md, "Startup migrations".
 */
@Component(service = {ProfileSensitiveFlagMigration.class, JahiaEventListener.class}, immediate = true)
public class ProfileSensitiveFlagMigration extends ElementsRedeployRetriggeredMigration {

    static final String JEXPERIENCE_MODULE_ID = "formidable-jexperience-engine";
    /** The flag's first home, a mixin of formidable-jexperience-engine, and the property it carried. */
    static final String RETIRED_MIXIN = "fmdbmix:jExperienceSensitiveField";
    static final String RETIRED_PROPERTY = "jExperienceSensitive";

    @Activate
    public void activate() {
        run();
    }

    @Override
    void run() {
        migrateBothWorkspaces(this::migrateWorkspace);
    }

    /** The elements, whose field types must define the new name, and the jExperience module, whose mixin is removed. */
    @Override
    boolean retriggeredBy(String moduleId) {
        return super.retriggeredBy(moduleId) || JEXPERIENCE_MODULE_ID.equals(moduleId);
    }

    /** @return the number of migrated fields */
    private int migrateWorkspace(JCRSessionWrapper session, String workspace) throws RepositoryException {
        // Removing a mixin the registry does not know fails, and so does writing a name the field's type does not
        // define yet (formidable-elements not redeployed: a type keeps the supertypes it resolved at its registration)
        boolean retiredMixinRegistered = NodeTypeRegistry.getInstance().hasNodeType(RETIRED_MIXIN);
        Tally tally = new Tally();
        JCRNodeIteratorWrapper fields = carriers(session);
        while (fields.hasNext()) {
            tally.add(migrateOne(session, (JCRNodeWrapper) fields.nextNode(), workspace, "Moved the sensitive flag of",
                    (s, field) -> moveFlag(s, field,
                            retiredMixinRegistered && field.getApplicablePropertyDefinition(FmdbProperty.PROFILE_SENSITIVE) != null)));
        }
        logSummary(workspace, tally,
                "[ProfileSensitiveFlagMigration] Moved the sensitive flag of {} field(s) to profileSensitive in workspace '{}'",
                "[ProfileSensitiveFlagMigration] {} field(s) in workspace '{}' wait for the (re)deploy of formidable-elements (their type"
                        + " does not define profileSensitive yet) or of formidable-jexperience-engine (the retired mixin is not registered);"
                        + " that redeploy or the next engine start moves the flag",
                "[ProfileSensitiveFlagMigration] {} field(s) still carry the retired sensitive mixin in workspace '{}' after the errors above;"
                        + " the next engine start or module redeploy retries them",
                "[ProfileSensitiveFlagMigration] No field carries the retired sensitive mixin in workspace '{}'");
        return tally.of(Outcome.MIGRATED);
    }

    /**
     * The mappable fields still listing the retired mixin, scoped to editorial content. Matched on the
     * jcr:mixinTypes value: a query on the retired mixin as a type would depend on its registration.
     */
    private static JCRNodeIteratorWrapper carriers(JCRSessionWrapper session) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + FmdbMixin.PROFILE_MAPPABLE_FIELD + "] AS f WHERE f.[jcr:mixinTypes] = '" + RETIRED_MIXIN
                        + "' AND ISDESCENDANTNODE(f, '/sites')",
                Query.JCR_SQL2);
        return (JCRNodeIteratorWrapper) query.execute().getNodes();
    }

    /**
     * Copies the flag under its new name — unless the new name already holds a value, which is then the
     * more recent one and wins — and removes the retired mixin, its property with it. Deferred while the
     * definitions are not ready: the field's type does not define the new name yet (formidable-elements not
     * redeployed) or the retired mixin is not registered (its module not deployed).
     */
    static Outcome moveFlag(JCRSessionWrapper session, JCRNodeWrapper field, boolean definitionsReady) throws RepositoryException {
        if (!definitionsReady) {
            return Outcome.DEFERRED;
        }
        session.checkout(field);
        if (!field.hasProperty(FmdbProperty.PROFILE_SENSITIVE) && field.hasProperty(RETIRED_PROPERTY)) {
            field.setProperty(FmdbProperty.PROFILE_SENSITIVE, field.getProperty(RETIRED_PROPERTY).getBoolean());
        }
        field.removeMixin(RETIRED_MIXIN);
        return Outcome.MIGRATED;
    }
}
