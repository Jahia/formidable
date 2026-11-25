package org.jahia.modules.formidable.quarantine.ops;

/**
 * Who the user of the current request is, for the operations and the GraphQL API: a manager of the quarantine of a
 * site holds the permission {@value #PERMISSION} on the site node, through the site role {@value #ROLE}. A server
 * administrator is a manager of every site.
 */
public interface Managers {

    /** The permission of a manager, declared by the module ({@code src/main/import/permissions.xml}). */
    String PERMISSION = "formidableQuarantineManage";

    /** The site role that grants {@link #PERMISSION}, created by the module ({@link ManagerRoleInitializer}). */
    String ROLE = "formidable-quarantine-manager";

    /** @return the name of the user of the current request, or {@code null} when there is none */
    String currentUserName();

    /** @return true for {@code root} and the members of the server group {@code administrators} */
    boolean isServerAdministrator();

    /**
     * @param siteKey the key of the site
     * @return true when the site has a live node, and the user is a server administrator or holds
     * {@link #PERMISSION} on that node; false for a key that names no site
     */
    boolean mayManage(String siteKey);

    /** The check of the platform, on the user that the authentication of Jahia set on the request. */
    static Managers ofThePlatform() {
        return new PlatformManagers();
    }
}
