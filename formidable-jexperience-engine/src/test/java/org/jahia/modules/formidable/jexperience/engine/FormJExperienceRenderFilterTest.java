package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.modules.jexperience.admin.ContextServerStatus;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRWorkspaceWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.Locale;
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

    /**
     * The node a page renders, with the render session behind it: the form itself on a plain page, or,
     * through a reference, a node with the same identifier whose path is no JCR path — and whose
     * session answers that same contextualised node for the identifier, as live does.
     */
    private static JCRNodeWrapper rendered(JCRNodeWrapper form, boolean throughAReference) throws RepositoryException {
        JCRNodeWrapper node = form;
        if (throughAReference) {
            node = mock(JCRNodeWrapper.class);
            when(node.getIdentifier()).thenReturn(FORM_UUID);
            when(node.getPath()).thenReturn("/sites/mysite/home/contact-page/pagecontent/theReference@/contact");
        }
        JCRSessionWrapper renderSession = mock(JCRSessionWrapper.class);
        JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class);
        when(workspace.getName()).thenReturn("live");
        when(renderSession.getWorkspace()).thenReturn(workspace);
        when(renderSession.getLocale()).thenReturn(Locale.ENGLISH);
        when(renderSession.getNodeByIdentifier(FORM_UUID)).thenReturn(node);
        when(node.getSession()).thenReturn(renderSession);
        return node;
    }

    /** A session of the filter's own: the one that resolves the identifier to the form where it lives. */
    private static JCRSessionWrapper ownSessionOver(JCRNodeWrapper form) throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNodeByIdentifier(FORM_UUID)).thenReturn(form);
        return session;
    }

    /** A filter reading through the given session, over a form whose mapped fields are given (null: reading fails). */
    private static FilterUnderTest filter(ContextServerService service, JCRSessionWrapper ownSession, Map<String, String> mappings) {
        FilterUnderTest filter = new FilterUnderTest(ownSession, mappings);
        filter.bindContextServerService(service);
        return filter;
    }

    /** Replaces the two seams: the session the filter opens, and the query of the mapped fields. */
    private static final class FilterUnderTest extends FormJExperienceRenderFilter {
        private final JCRSessionWrapper ownSession;
        private final Map<String, String> mappings;
        private String queriedPath;
        private String openedWorkspace;

        private FilterUnderTest(JCRSessionWrapper ownSession, Map<String, String> mappings) {
            this.ownSession = ownSession;
            this.mappings = mappings;
        }

        @Override
        <T> T inOwnSession(String workspace, Locale locale, JCRCallback<T> callback) throws RepositoryException {
            openedWorkspace = workspace;
            return callback.doInJCR(ownSession);
        }

        @Override
        Map<String, String> mappedFields(JCRNodeWrapper form) throws RepositoryException {
            if (mappings == null) {
                throw new RepositoryException("gone");
            }
            queriedPath = form.getPath();
            return mappings;
        }
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
        JCRNodeWrapper form = form("Contact us");
        String out = filter(configured("mysite"), ownSessionOver(form), mappings)
                .prepend("<form></form>", "mysite", rendered(form, false));

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
        JCRNodeWrapper form = form("</script><b>&");
        String out = filter(configured("mysite"), ownSessionOver(form), Map.of())
                .prepend("", "mysite", rendered(form, false));

        assertTrue(out.contains("\"name\":\"\\u003c/script\\u003e\\u003cb\\u003e\\u0026\""), out);
        assertTrue(out.contains("\"mappings\":[]}"), out);
    }

    @Test
    void theFormRenderedThroughAReferenceIsNamedWhereItLives() throws Exception {
        // Verifies the reason the filter opens a session of its own: a form placed through a reference
        // renders contextualised under it, and the render session answers that contextualised node for
        // the identifier — so the block must carry the form's own path, the one the mapping rule names,
        // and the fields must be looked up there.
        JCRNodeWrapper form = form("Contact us");
        FilterUnderTest filter = filter(configured("mysite"), ownSessionOver(form), Map.of("firstName", "firstName"));

        String out = filter.prepend("<form></form>", "mysite", rendered(form, true));

        assertTrue(out.contains("\"path\":\"/sites/mysite/contents/contact\""), out);
        assertEquals("/sites/mysite/contents/contact", filter.queriedPath);
        assertEquals("live", filter.openedWorkspace);
        assertTrue(out.contains("{\"field\":\"firstName\",\"property\":\"firstName\"}"), out);
    }

    @Test
    void nothingIsWrittenOutsideAConfiguredSiteOrWhenTheFormCannotBeRead() throws Exception {
        // Verifies the silences: a site without jExperience settings, no site at all (a form outside a site), and a repository
        // failure while reading the form each leave the form's markup untouched.
        JCRNodeWrapper form = form("Contact");
        assertEquals("<form></form>", filter(mock(ContextServerService.class), ownSessionOver(form), Map.of())
                .prepend("<form></form>", "mysite", rendered(form, false)));
        assertEquals("<form></form>", filter(configured("mysite"), ownSessionOver(form), Map.of())
                .prepend("<form></form>", null, rendered(form, false)));
        assertEquals("<form></form>", filter(configured("mysite"), ownSessionOver(form), null)
                .prepend("<form></form>", "mysite", rendered(form, false)));
    }
}
