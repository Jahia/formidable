import org.jahia.services.content.JCRObservationManager
import org.jahia.services.content.JCRSessionFactory

// Simulates a form stored before its 'fields'/'actions' lists carried a title, by
// removing jcr:title from every translation subnode of both lists, in both workspaces.
def formPath = "__FORM_PATH__"

def report = []
["default", "live"].each { workspace ->
    def session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)
    if (!session.nodeExists(formPath)) {
        report << "${workspace}: missing node ${formPath}"
        return
    }
    def form = session.getNode(formPath)
    ["fields", "actions"].each { listName ->
        if (!form.hasNode(listName)) {
            return
        }
        def list = form.getNode(listName)
        list.getI18Ns().each { translation ->
            if (translation.hasProperty("jcr:title")) {
                translation.getProperty("jcr:title").remove()
            }
        }
    }
    // A 0.3 site never wrote these nodes in live directly: keep Jahia's UGCListener
    // from marking the simulated legacy state as live-owned (#281).
    JCRObservationManager.setAllEventListenersDisabled(workspace == "live")
    try {
        session.save()
    } finally {
        JCRObservationManager.setAllEventListenersDisabled(false)
    }
    report << "${workspace}: removed list titles under ${formPath}"
}

return report.join(" | ")
