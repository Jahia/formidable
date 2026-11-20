import org.jahia.services.content.JCRSessionFactory

import java.nio.charset.StandardCharsets

// Replaces, in live, the bytes of a quarantine item (__PATH__) with __TEXT__, and keeps its sha256: the bytes change
// after intake, as an attacker with write access to the repository would change them (Q09).
// cy.executeGroovy only surfaces installed/failed, so a missing item throws and the caller sees ".failed".
def path = "__PATH__"
def text = "__TEXT__".replace("\\n", "\n")
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def item = session.getNode(path)
if (!item.isNodeType("fmdbquar:item") || !item.hasProperty("data")) {
    throw new IllegalStateException("${path} is no quarantine item with bytes")
}
def binary = session.getValueFactory().createBinary(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
try {
    item.setProperty("data", binary)
} finally {
    binary.dispose()
}
session.save()
return "The bytes of ${path} were replaced"
