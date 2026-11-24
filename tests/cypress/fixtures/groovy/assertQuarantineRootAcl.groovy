import org.jahia.services.content.JCRSessionFactory

// Asserts, in live, that the quarantine root cuts its ACL inheritance and holds no access control entry.
// cy.executeGroovy only surfaces installed/failed, so a violation throws and the caller sees ".failed".
def path = "__PATH__"
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def root = session.getNode(path)
if (!root.getAclInheritanceBreak()) {
    throw new IllegalStateException("The quarantine root ${path} inherits the ACL of the site")
}
if (root.hasNode("j:acl")) {
    def aces = root.getNode("j:acl").getNodes().findAll { it.isNodeType("jnt:ace") }.collect { it.getName() }
    if (!aces.isEmpty()) {
        throw new IllegalStateException("The quarantine root ${path} holds access control entries: ${aces}")
    }
}
return "The quarantine root ${path} cuts its ACL inheritance and holds no entry"
