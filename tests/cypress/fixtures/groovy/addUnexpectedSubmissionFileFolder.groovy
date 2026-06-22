import org.jahia.services.content.JCRSessionFactory

def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def submissionPath = "__SUBMISSION_PATH__"
def folderName = "__FOLDER_NAME__"
def filesPath = "${submissionPath}/files"

if (!session.nodeExists(filesPath)) {
    return "Missing files folder at ${filesPath}"
}

def filesNode = session.getNode(filesPath)
if (!filesNode.hasNode(folderName)) {
    filesNode.addNode(folderName, "jnt:folder")
    session.save()
}

return "Added folder ${folderName} under ${filesPath}"
