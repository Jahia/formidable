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

(admin.listConfigurations("(service.factoryPid=" + factoryPid + ")") ?: []).each { configuration ->
    configs.deleteConfig(configs.getConfig(configuration.pid))
}
entries.each { entry ->
    def config = configs.getConfig(factoryPid, entry.id)
    entry.each { key, value -> config.values.setProperty(key, String.valueOf(value)) }
    configs.storeConfig(config)
}
log.info("replaceFactoryConfigurations " + factoryPid + ": " + entries.collect { it.id })
