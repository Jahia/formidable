package org.jahia.modules.formidable.quarantine.ops;

/**
 * Who the user of the current request is, for the operations: a manager of the quarantine of a form holds the
 * permission {@value #PERMISSION} on the form, through the role {@value #ROLE}. A server administrator is a manager of
 * every form.
 */
public interface Managers {

    /** The permission of a manager, declared by the module ({@code src/main/import/permissions.xml}). */
    String PERMISSION = "formidableQuarantineManage";

    /** The role that grants {@link #PERMISSION}, created by the module ({@link ManagerRoleInitializer}). */
    String ROLE = "formidable-quarantine-manager";

    /** @return the name of the user of the current request, or {@code null} when there is none */
    String currentUserName();

    /** @return true for {@code root} and the members of the server group {@code administrators} */
    boolean isServerAdministrator();

    /**
     * @param formId the identifier of the form, in live
     * @return true when the user is a server administrator, or holds {@link #PERMISSION} on the live form
     */
    boolean mayManage(String formId);

    /** The check of the platform, on the user that the authentication of Jahia set on the request. */
    static Managers ofThePlatform() {
        return new PlatformManagers();
    }
}
