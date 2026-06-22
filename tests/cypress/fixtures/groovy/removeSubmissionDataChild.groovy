import org.jahia.services.content.JCRSessionFactory

def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def submissionPath = "__SUBMISSION_PATH__"
def dataPath = "${submissionPath}/data"

if (!session.nodeExists(dataPath)) {
    return "Missing data child at ${dataPath}"
}

session.getNode(dataPath).remove()
session.save()
return "Removed data child from ${submissionPath}"
