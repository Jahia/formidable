import org.jahia.services.content.JCRSessionFactory

def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def submissionPath = "__SUBMISSION_PATH__"
def propertyName = "__PROPERTY_NAME__"
def propertyValue = "__PROPERTY_VALUE__"
def dataPath = "${submissionPath}/data"

if (!session.nodeExists(dataPath)) {
    return "Missing data child at ${dataPath}"
}

def dataNode = session.getNode(dataPath)
dataNode.setProperty(propertyName, propertyValue)
session.save()
return "Added property ${propertyName} on ${dataPath}"
