import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.services.usermanager.JahiaGroupManagerService
import org.jahia.services.usermanager.JahiaUserManagerService

// The fixture of the Create content action (spec 90): one folder per supplier, in the content tree and in the files
// tree, each closed to everyone but the supplier's own group, published to live with its ACL.
//
//   /sites/<site>/contents/orvane-briefs/<supplier>   and   /sites/<site>/files/orvane-briefs/<supplier>
//   groups  cyp-supplier-<supplier>  (global), the user __USER__ is added to the groups of __GROUPS__ (comma separated)
//
// The "ghost" supplier is the misconfiguration the action must survive: its folders exist and its group exists, but the
// folders grant the role to ANOTHER group, so a member of cyp-supplier-ghost may not read them.
def site = "__SITE__"
def userName = "__USER__"
def memberOf = "__GROUPS__".split(",").collect { it.trim() }.findAll { it }
def suppliers = ["northlight", "harbor", "ghost"]
def grantedTo = ["northlight": "northlight", "harbor": "harbor", "ghost": "northlight"]

def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def groups = JahiaGroupManagerService.getInstance()
def users = JahiaUserManagerService.getInstance()

suppliers.each { supplier ->
    def name = "cyp-supplier-${supplier}"
    if (groups.lookupGroup(null, name, session) == null) {
        groups.createGroup(null, name, new Properties(), false, session)
    }
}
session.save()

def user = users.lookupUser(userName, session)
if (user == null) {
    throw new IllegalStateException("The user ${userName} does not exist: create it first")
}
suppliers.each { supplier ->
    def group = groups.lookupGroup(null, "cyp-supplier-${supplier}", session)
    def isMember = memberOf.contains("cyp-supplier-${supplier}")
    if (isMember) {
        group.addMember(user)
    } else if (group.isMember(user)) {
        group.removeMember(user)
    }
}
session.save()

def ids = []
[["contents", "jnt:contentFolder"], ["files", "jnt:folder"]].each { tree ->
    def parent = session.getNode("/sites/${site}/${tree[0]}")
    def root = parent.hasNode("orvane-briefs") ? parent.getNode("orvane-briefs") : parent.addNode("orvane-briefs", tree[1])
    suppliers.each { supplier ->
        def folder = root.hasNode(supplier) ? root.getNode(supplier) : root.addNode(supplier, tree[1])
        folder.setAclInheritanceBreak(true)
        folder.grantRoles("g:cyp-supplier-${grantedTo[supplier]}", ["reader"] as Set)
    }
    session.save()
    ids << root.getIdentifier()
    JCRPublicationService.getInstance().publishByMainId(root.getIdentifier())
}

return ids.join("|")
