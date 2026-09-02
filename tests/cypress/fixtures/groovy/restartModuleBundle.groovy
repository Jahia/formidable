import org.jahia.osgi.FrameworkService

// Restarts the given module bundle, without having to know the installed
// version. Restarting formidable-elements fires TemplatePackageRedeployedEvent,
// the trigger of the engine's redeploy-retriggered content migrations — the run
// that does the migration work on the engine-first upgrade path.
def moduleId = "__MODULE_ID__"
def bundle = FrameworkService.getBundleContext().getBundles().find {
    it.getSymbolicName() == moduleId
}

if (bundle == null) {
    return "${moduleId} bundle not found"
}

bundle.stop()
bundle.start()
return "${moduleId} restarted (state ${bundle.getState()})"
