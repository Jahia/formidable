import org.jahia.osgi.FrameworkService

// Restarts the engine bundle so its startup-time content migrations run again,
// without having to know the installed version.
def bundle = FrameworkService.getBundleContext().getBundles().find {
    it.getSymbolicName() == "formidable-engine"
}

if (bundle == null) {
    return "formidable-engine bundle not found"
}

bundle.stop()
bundle.start()
return "formidable-engine restarted (state ${bundle.getState()})"
