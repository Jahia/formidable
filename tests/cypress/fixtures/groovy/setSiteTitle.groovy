import org.jahia.services.content.JCRSessionFactory

// Titles a site in both playground languages. createSite (the @jahia/cypress helper) titles a
// site after its key, and the site switcher shows the title: the playground sites get a readable one.
def path = "/sites/__SITE_KEY__"
['en', 'fr'].each { language ->
    def session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", Locale.forLanguageTag(language), null)
    session.getNode(path).setProperty("j:title", "__TITLE__")
    session.save()
}
return "titled __SITE_KEY__ as __TITLE__"
