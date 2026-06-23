import org.jahia.services.content.JCRSessionFactory

def paths = "__PATHS__".split("\\|").findAll { it != null && !it.isBlank() }
def factory = JCRSessionFactory.getInstance()

["default", "live"].each { workspace ->
    def session = factory.getCurrentSystemSession(workspace, null, null)
    paths.each { path ->
        if (session.nodeExists(path)) {
            session.getNode(path).remove()
        }
    }
    session.save()
}

return "Deleted paths in default/live: ${paths.join(', ')}"
