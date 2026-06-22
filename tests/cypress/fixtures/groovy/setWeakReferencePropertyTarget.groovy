import org.jahia.services.content.JCRSessionFactory

def workspace = "__WORKSPACE__".trim().toLowerCase()
def sourcePath = "__SOURCE_PATH__"
def propertyName = "__PROPERTY_NAME__"
def targetPath = "__TARGET_PATH__"

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)

if (!session.nodeExists(sourcePath)) {
    return "Missing source node at ${sourcePath}"
}

if (!session.nodeExists(targetPath)) {
    return "Missing target node at ${targetPath}"
}

def sourceNode = session.getNode(sourcePath)
def targetNode = session.getNode(targetPath)
def weakReferenceValue = session.getValueFactory().createValue(targetNode, true)

sourceNode.setProperty(propertyName, weakReferenceValue)
session.save()

return "Updated ${propertyName} on ${sourcePath} to target ${targetPath} in ${workspace}"
