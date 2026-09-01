package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.observation.JahiaEventListener;
import org.jahia.services.templates.JahiaTemplateManagerService.TemplatePackageRedeployedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EventObject;

/**
 * Base for the startup content migrations that must also re-run when the
 * formidable-elements module is (re)deployed. The engine usually starts (and is
 * upgraded) before the elements, and a migration that writes properties or mixins
 * resolved through the element types can only succeed once that module has
 * registered its upgraded definitions: on the engine-first upgrade path the
 * elements-redeploy run is the one that does the work, the engine-activation run
 * having failed against the previous definitions.
 *
 * <p>Concrete migrations keep their own {@code @Component} declaration and must
 * expose {@link JahiaEventListener} as a service interface to receive the event.
 *
 * <p>Lifecycle: to be removed in 0.5 with the migrations it retriggers — see
 * docs/upgrade-notes.md, "Startup migrations".
 */
abstract class ElementsRedeployRetriggeredMigration implements JahiaEventListener<EventObject> {

    static final String ELEMENTS_MODULE_ID = "formidable-elements";

    @SuppressWarnings("unchecked")
    private static final Class<EventObject>[] ACCEPTED_EVENT_TYPES = new Class[]{TemplatePackageRedeployedEvent.class};

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** The redeploy event carries the module id as its source. */
    @Override
    public void onEvent(EventObject event) {
        if (event instanceof TemplatePackageRedeployedEvent && ELEMENTS_MODULE_ID.equals(event.getSource())) {
            log.info("[{}] {} (re)deployed, re-running the migration", getClass().getSimpleName(), ELEMENTS_MODULE_ID);
            run();
        }
    }

    @Override
    public Class<EventObject>[] getEventTypes() {
        return ACCEPTED_EVENT_TYPES;
    }

    /** Runs the whole migration; keyed on content state, so re-running is a no-op. */
    abstract void run();
}
