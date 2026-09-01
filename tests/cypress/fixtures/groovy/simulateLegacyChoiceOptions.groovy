import groovy.json.JsonOutput
import org.jahia.services.content.JCRSessionFactory

// Simulates a 0.3-era choice field: the option list lived in a legacy i18n
// property ('options' on selects, 'choices' on radios/checkboxes) whose values
// sit on the j:translation_* subnodes, where residual definitions keep them
// writable after the CND removal. Pairs are "value:Label" entries. 0.3 let the
// values themselves diverge between languages, so the language is a parameter
// and the translation node is created when the language was never edited.
def fieldPath = "__FIELD_PATH__"
def legacyProperty = "__LEGACY_PROPERTY__"
def language = "__LANGUAGE__"
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
    def translation = node.getOrCreateI18N(Locale.forLanguageTag(language))
    translation.setProperty(legacyProperty, legacyValues)
    session.save()
    report << "${workspace}: set ${legacyProperty} on ${translation.path}"
}

return report.join(" | ")
