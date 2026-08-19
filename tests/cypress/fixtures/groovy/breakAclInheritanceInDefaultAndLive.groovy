import org.jahia.services.content.JCRSessionFactory

// Hides a node from every non-system session by breaking its ACL inheritance,
// directly in both workspaces so no publication is needed to reach live.
def path = "__PATH__"
def factory = JCRSessionFactory.getInstance()

["default", "live"].each { workspace ->
    def session = factory.getCurrentSystemSession(workspace, null, null)
    def node = session.getNode(path)
    node.setAclInheritanceBreak(true)
    session.save()
    if (!node.getAclInheritanceBreak()) {
        throw new IllegalStateException("ACL inheritance still active on ${path} in ${workspace}")
    }
}

return "ACL inheritance broken in default/live for: ${path}"
