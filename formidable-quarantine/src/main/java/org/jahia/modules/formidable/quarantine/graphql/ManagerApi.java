package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.formidable.quarantine.ops.Managers;
import org.jahia.modules.formidable.quarantine.ops.QuarantineOperations;
import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The entry of the GraphQL API into the module, an OSGi service: the resolvers of graphql-core are not DS components,
 * so they look this service up. It opens the quarantine of a site only for a manager of the site: the permission
 * {@value Managers#PERMISSION} on the site node, checked in a session of the user of the request.
 */
@Component(service = ManagerApi.class, immediate = true)
public class ManagerApi {

    private final Managers managers;
    private final FormReader forms;
    private QuarantineStore store;
    private CheckStore checkStore;
    private QuarantineOperations operations;
    private QuarantineRetention retention;

    public ManagerApi() {
        this(Managers.ofThePlatform(), new JcrFormReader());
    }

    ManagerApi(Managers managers, FormReader forms) {
        this.managers = managers;
        this.forms = forms;
    }

    @Reference
    public void setStore(QuarantineStore store) {
        this.store = store;
    }

    @Reference
    public void setCheckStore(CheckStore checkStore) {
        this.checkStore = checkStore;
    }

    @Reference
    public void setOperations(QuarantineOperations operations) {
        this.operations = operations;
    }

    @Reference
    public void setRetention(QuarantineRetention retention) {
        this.retention = retention;
    }

    /**
     * The quarantine of the site, for the user of the request.
     *
     * @return {@code null} when the key names no site, or a site that the user does not manage: the two answers are
     * the same
     */
    SiteQuarantine open(String siteKey) {
        if (!HeldIds.isSiteKey(siteKey) || !managers.mayManage(siteKey)) {
            return null;
        }
        return new SiteQuarantine(siteKey, store, checkStore, operations::apply, retention::purge, managers, forms);
    }
}
