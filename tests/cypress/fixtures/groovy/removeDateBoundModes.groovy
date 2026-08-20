import org.jahia.services.content.JCRSessionFactory

// Simulates a field stored before the bound modes existed: the fixed min/max
// values are kept in place, but the fmdb:minBoundMode / fmdb:maxBoundMode
// properties are removed — the exact trigger state of DateBoundsContentMigration
// (mode absent, fixed value present).
def fieldPath = "__FIELD_PATH__"

def report = []
["default", "live"].each { workspace ->
    def session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)
    if (!session.nodeExists(fieldPath)) {
        report << "${workspace}: missing node ${fieldPath}"
        return
    }

    def node = session.getNode(fieldPath)
    ["fmdb:minBoundMode", "fmdb:maxBoundMode"].each { property ->
        if (node.hasProperty(property)) {
            node.getProperty(property).remove()
        }
    }
    session.save()
    report << "${workspace}: removed bound modes on ${node.path}"
}

return report.join(" | ")
