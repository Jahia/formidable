import org.jahia.services.content.JCRSessionFactory

// Writes a logic rule straight into the LIVE workspace. The authoring save cleanup
// only watches the default workspace, so this reproduces rules stored by an older
// module version that never went through a save with the current engine.
def session = JCRSessionFactory.getInstance().getCurrentSystemSession("live", null, null)
def node = session.getNode("__FIELD_PATH__")
node.setProperty("logics", (String[]) ['__RULE_JSON__'])
session.save()
return "logics set on __FIELD_PATH__ (live)"
