import org.jahia.services.content.JCRSessionFactory

def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def resultsPath = "__RESULTS_PATH__"

if (!session.nodeExists(resultsPath)) {
    return "Missing results node at ${resultsPath}"
}

def resultsNode = session.getNode(resultsPath)
if (resultsNode.hasNode("submissions")) {
    resultsNode.getNode("submissions").remove()
    session.save()
    return "Removed submissions child from ${resultsPath}"
}

return "No submissions child found under ${resultsPath}"
