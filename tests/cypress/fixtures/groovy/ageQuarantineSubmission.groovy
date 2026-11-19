import org.jahia.services.content.JCRSessionFactory

// Moves, in live, the last change of the state of a held submission (__PATH__) __DAYS__ days back, as if it had
// stayed in its state that long: the retention reads changedAt (Q18). No spec waits for time.
// cy.executeGroovy only surfaces installed/failed, so a missing held submission throws and the caller sees ".failed".
def path = "__PATH__"
def days = Integer.parseInt("__DAYS__")
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def held = session.getNode(path)
if (!held.isNodeType("fmdbquar:heldSubmission")) {
    throw new IllegalStateException("${path} is no held submission")
}
def changedAt = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
changedAt.add(Calendar.DAY_OF_MONTH, -days)
held.setProperty("changedAt", changedAt)
session.save()
return "The last change of ${path} is ${days} days back"
