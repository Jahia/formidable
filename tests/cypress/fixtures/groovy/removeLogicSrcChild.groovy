import org.jahia.services.content.JCRSessionFactory

def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null)
def targetPath = "__TARGET_PATH__"
def logicId = "__LOGIC_ID__"
def logicChildPath = "${targetPath}/logicsSrc/${logicId}"

if (!session.nodeExists(logicChildPath)) {
    return "Missing logic child at ${logicChildPath}"
}

session.getNode(logicChildPath).remove()
session.save()
return "Removed logic child ${logicChildPath}"
