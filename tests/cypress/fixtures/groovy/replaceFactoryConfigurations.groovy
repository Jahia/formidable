// Replaces every configuration of one factory — the entries of an administrator's list, one file each — by the
// entries given, through Jahia's configuration service, the one the provisioning API uses: the files of
// karaf/etc/<factory PID>-<id>.cfg are written and loaded, those of the ids no longer listed deleted.
// Replacements: __FACTORY_PID__, and __ENTRIES__ as a JSON array of objects, one per entry, its id included.
import groovy.json.JsonSlurper
import org.jahia.osgi.BundleUtils

def factoryPid = "__FACTORY_PID__"
def entries = new JsonSlurper().parseText('''__ENTRIES__''')
def admin = BundleUtils.getOsgiService("org.osgi.service.cm.ConfigurationAdmin", null)
def configs = BundleUtils.getOsgiService("org.jahia.services.modulemanager.spi.ConfigService", null)

// An entry wanted again under the file this script writes is updated in place, never deleted first: deleting a
// configuration and writing its file back within milliseconds loses it — fileinstall handles the deletion event
// late and removes the file it finds, the fresh one (seen in CI, 2026-10-01).
def wanted = entries.collect { it.id } as Set
(admin.listConfigurations("(service.factoryPid=" + factoryPid + ")") ?: []).each { configuration ->
    def id = configuration.properties.get("id")
    def file = String.valueOf(configuration.properties.get("felix.fileinstall.filename"))
    def kept = id != null && wanted.contains(id) && file.endsWith("/" + factoryPid + "-" + id + ".cfg")
    if (!kept) {
        configs.deleteConfig(configs.getConfig(configuration.pid))
    }
}
entries.each { entry ->
    def config = configs.getConfig(factoryPid, entry.id)
    entry.each { key, value -> config.values.setProperty(key, String.valueOf(value)) }
    configs.storeConfig(config)
}
log.info("replaceFactoryConfigurations " + factoryPid + ": " + entries.collect { it.id })
