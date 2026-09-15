package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.modules.jexperience.admin.ContextServerStatus;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the filter writes before a form: the configuration block the client script reads and the
 * script tag — and nothing at all outside a jExperience-configured site or when the form cannot be read.
 */
class FormJExperienceRenderFilterTest {

    private static final String FORM_UUID = "8f7e2a10-0000-4000-8000-000000000001";

    private static JCRNodeWrapper form(String title) throws RepositoryException {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.getIdentifier()).thenReturn(FORM_UUID);
        when(form.getDisplayableName()).thenReturn(title);
        when(form.getPath()).thenReturn("/sites/mysite/contents/contact");
        return form;
    }

    /** A filter over a form whose mapped fields are given, or whose reading fails when they are null. */
    private static FormJExperienceRenderFilter filter(ContextServerService service, Map<String, String> mappings) {
        FormJExperienceRenderFilter filter = new FormJExperienceRenderFilter() {
            @Override
            Map<String, String> mappedFields(JCRNodeWrapper form) throws RepositoryException {
                if (mappings == null) {
                    throw new RepositoryException("gone");
                }
                return mappings;
            }
        };
        filter.bindContextServerService(service);
        return filter;
    }

    private static ContextServerService configured(String siteKey) {
        ContextServerService service = mock(ContextServerService.class);
        when(service.getContextServerStatus(siteKey)).thenReturn(mock(ContextServerStatus.class));
        return service;
    }

    @Test
    void writesTheConfigurationBlockAndTheScriptBeforeTheForm() throws Exception {
        // Verifies the contribution: a JSON block keyed on the form's UUID with identifier, title, path and
        // the mapped fields in order, then the script tag, both before the form's own markup.
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("firstName", "firstName");
        mappings.put("topics", "interests");
        String out = filter(configured("mysite"), mappings).prepend("<form></form>", "mysite", form("Contact us"));

        assertEquals("<script type=\"application/json\" data-formidable-jxp=\"" + FORM_UUID + "\">"
                + "{\"formId\":\"" + FORM_UUID + "\",\"name\":\"Contact us\",\"path\":\"/sites/mysite/contents/contact\","
                + "\"mappings\":[{\"field\":\"firstName\",\"property\":\"firstName\"},{\"field\":\"topics\",\"property\":\"interests\"}]}</script>\n"
                + "<script src=\"" + FormJExperienceRenderFilter.SCRIPT_URL + "\" defer></script>\n"
                + "<form></form>", out);
    }

    @Test
    void aFormWithoutMappingsStillGetsItsBlockAndATitleIsEscaped() throws Exception {
        // Verifies the two edges of the block: an empty mappings list (a form a goal may still watch) and a
        // title that could otherwise close the script block.
        String out = filter(configured("mysite"), Map.of()).prepend("", "mysite", form("</script><b>&"));

        assertTrue(out.contains("\"name\":\"\\u003c/script\\u003e\\u003cb\\u003e\\u0026\""), out);
        assertTrue(out.contains("\"mappings\":[]}"), out);
    }

    @Test
    void nothingIsWrittenOutsideAConfiguredSiteOrWhenTheFormCannotBeRead() throws Exception {
        // Verifies the silences: a site without jExperience settings, no site at all (a form outside a site), and a repository
        // failure while reading the form each leave the form's markup untouched.
        ContextServerService unconfigured = mock(ContextServerService.class);
        assertEquals("<form></form>", filter(unconfigured, Map.of()).prepend("<form></form>", "mysite", form("Contact")));
        assertEquals("<form></form>", filter(configured("mysite"), Map.of()).prepend("<form></form>", null, form("Contact")));
        assertEquals("<form></form>", filter(configured("mysite"), null).prepend("<form></form>", "mysite", form("Contact")));
    }
}
