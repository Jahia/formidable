import org.jahia.services.content.JCRSessionFactory

// Asserts, in live, that the quarantine tree of a form is inert: exactly __ITEMS__ items, each with its binary, and
// no file node, no resource node, no extracted text and no thumbnail anywhere under it.
// cy.executeGroovy only surfaces installed/failed, so a violation throws and the caller sees ".failed".
def path = "__PATH__"
def expectedItems = "__ITEMS__".toInteger()
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def problems = []
def items = 0

def visit
visit = { node ->
    if (node.isNodeType("fmdbquar:item")) {
        items++
        if (!node.hasProperty("data")) {
            problems << "the item ${node.getPath()} holds no binary"
        }
    }
    ["jnt:file", "jnt:resource", "nt:file", "nt:resource"].each { type ->
        if (node.isNodeType(type)) {
            problems << "${node.getPath()} is a ${type}"
        }
    }
    if (node.hasProperty("j:extractedText")) {
        problems << "${node.getPath()} holds j:extractedText"
    }
    if (node.getName().startsWith("thumbnail") || node.isNodeType("jmix:image")) {
        problems << "${node.getPath()} is a thumbnail or an image node"
    }
    node.getNodes().each { child -> visit(child) }
}
visit(session.getNode(path))

if (items != expectedItems) {
    problems << "${items} items under ${path}, ${expectedItems} expected"
}
if (!problems.isEmpty()) {
    throw new IllegalStateException("The quarantine tree is not inert: ${problems}")
}
return "The quarantine tree ${path} holds ${items} inert items"
