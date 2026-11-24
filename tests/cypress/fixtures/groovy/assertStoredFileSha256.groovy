import org.jahia.services.content.JCRSessionFactory

import java.security.MessageDigest

// Asserts, in live, that the stored submission __PATH__ holds exactly one file, and that the SHA-256 of its bytes is
// __SHA256__: the bytes released are the bytes the checks passed (Q02).
// cy.executeGroovy only surfaces installed/failed, so a violation throws and the caller sees ".failed".
def path = "__PATH__"
def expected = "__SHA256__"
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)

def files = []
def collect
collect = { node ->
    if (node.isNodeType("jnt:file")) {
        files << node
    }
    node.getNodes().each { child -> collect(child) }
}
collect(session.getNode(path))
if (files.size() != 1) {
    throw new IllegalStateException("${path} holds ${files.size()} files, not 1")
}
def binary = files[0].getNode("jcr:content").getProperty("jcr:data").getBinary()
def actual
try {
    actual = MessageDigest.getInstance("SHA-256").digest(binary.getStream().withCloseable { it.bytes }).encodeHex().toString()
} finally {
    binary.dispose()
}
if (actual != expected) {
    throw new IllegalStateException("The stored file ${files[0].getName()} has SHA-256 ${actual}, not ${expected}")
}
return "The stored file ${files[0].getName()} has the SHA-256 of the item"
