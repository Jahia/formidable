import org.jahia.services.content.JCRSessionFactory

// Asserts, in live, that a node under __PATH__ carries j:extractedText: the text extraction of the core has run on a
// file stored there. A caller polls it. cy.executeGroovy only surfaces installed/failed, so "not yet" throws.
def path = "__PATH__"
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)

def found
found = { node ->
    node.hasProperty("j:extractedText") || node.getNodes().any { child -> found(child) }
}
if (!found(session.getNode(path))) {
    throw new IllegalStateException("No j:extractedText under ${path} yet")
}
return "j:extractedText found under ${path}"
