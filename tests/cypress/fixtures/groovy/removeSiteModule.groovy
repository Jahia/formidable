import org.jahia.services.content.JCRSessionFactory

// Removes a module from a site's installed list, as the 0.3 -> 0.4 identity-swap
// uninstall does — the state ElementsSiteReactivation exists to heal.
def sitePath = "__SITE_PATH__"
def moduleId = "__MODULE_ID__"
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def site = session.getNode(sitePath)
def values = site.getProperty("j:installedModules").getValues().collect { it.getString() }
values.remove(moduleId)
site.setProperty("j:installedModules", values as String[])
session.save()
return "removed ${moduleId} from ${sitePath}: ${values.join(',')}"
