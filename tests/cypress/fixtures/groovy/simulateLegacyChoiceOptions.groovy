import groovy.json.JsonOutput
import org.jahia.services.content.JCRSessionFactory

// Simulates a 0.3-era choice field: the option list lived in a legacy i18n
// property ('options' on selects, 'choices' on radios/checkboxes) whose values
// sit on the j:translation_* subnodes, where residual definitions keep them
// writable after the CND removal. Pairs are "value:Label" entries.
def fieldPath = "__FIELD_PATH__"
def legacyProperty = "__LEGACY_PROPERTY__"
def selectedValue = "__SELECTED__"
def legacyValues = "__PAIRS__".split(",").collect {
    def pair = it.split(":", 2)
    JsonOutput.toJson([value: pair[0], label: pair[1], selected: pair[0] == selectedValue])
} as String[]

def report = []
["default", "live"].each { workspace ->
    def session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null)
    if (!session.nodeExists(fieldPath)) {
        report << "${workspace}: missing node ${fieldPath}"
        return
    }

    def node = session.getNode(fieldPath)
    def translations = node.getNodes("j:translation_*")
    if (!translations.hasNext()) {
        report << "${workspace}: no translation node under ${fieldPath}"
        return
    }

    def translation = translations.nextNode()
    translation.setProperty(legacyProperty, legacyValues)
    session.save()
    report << "${workspace}: set ${legacyProperty} on ${translation.path}"
}

return report.join(" | ")
