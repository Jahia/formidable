package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.action.CreateContentFormAction;
import org.jahia.modules.formidable.content.convert.Reference;
import org.jahia.modules.formidable.content.plan.ContentPlan;
import org.jahia.modules.formidable.content.platform.Submitters;
import org.jahia.modules.formidable.content.store.ContentWriter;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.JCRWorkspaceWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedNodeDefinition;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateContentFormActionTest {

    private static final String BRIEFS = "/sites/orvane/contents/briefs";
    private static final String FILES = "/sites/orvane/files/briefs";
    private static final String NORTHLIGHT = "orvane-supplier-northlight";

    private final Map<String, String> strings = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Map<String, JCRNodeWrapper> references = new HashMap<>();
    private final List<String> unreadable = new ArrayList<>();
    private final List<String> existingNodes = new ArrayList<>();
    private final List<String> readAttempts = new ArrayList<>();
    private final Map<String, JCRNodeWrapper> readable = new HashMap<>();

    private JCRNodeWrapper briefs;
    private JCRNodeWrapper files;
    private JCRNodeWrapper actionNode;
    private JCRSessionWrapper visitor;
    private JahiaUser user;
    private Fakes.Catalog catalog;
    private FakeWriter writer;
    private FakeSubmitters submitters;
    private CreateContentFormAction action;
    private Map<String, List<String>> values;
    private List<SubmittedFile> uploads;
    private String referer;

    static final class FakeWriter implements ContentWriter {
        final List<ContentPlan> plans = new ArrayList<>();
        final List<JahiaUser> users = new ArrayList<>();
        RepositoryException failure;

        @Override
        public Created create(ContentPlan plan, JahiaUser submitter) throws RepositoryException {
            if (failure != null) {
                throw failure;
            }
            plans.add(plan);
            users.add(submitter);
            return new Created("content-id", plan.parentPath() + "/summer-launch-film", List.of());
        }
    }

    static final class FakeSubmitters implements Submitters {
        List<String> groups = new ArrayList<>();
        String organization = "Northlight Studio";

        @Override
        public List<String> groupPaths(JahiaUser user) {
            return groups;
        }

        @Override
        public String organization(JahiaUser user) {
            return organization;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        briefs = folder(BRIEFS, "northlight");
        files = folder(FILES, "northlight");
        references.put("parentFolder", briefs);
        references.put("filesFolder", files);
        strings.put("contentType", Fakes.Catalog.CONTENT_TYPE);
        strings.put("groupPrefix", "orvane-supplier-");
        strings.put("titleField", "title");
        lists.put("fieldMappings", List.of("summary=description"));
        lists.put("fileMappings", List.of("media=media"));
        lists.put("fixedValues", List.of("submissionStatus=submitted"));
        lists.put("requiredPairs", List.of("captions<-media:video/*"));
        lists.put("submitterProperty", List.of());
        strings.put("submitterProperty", "submittedBy");
        actionNode = actionNode();

        visitor = mock(JCRSessionWrapper.class);
        user = mock(JahiaUser.class);
        when(user.getUsername()).thenReturn("ada@northlight.example");
        when(user.getName()).thenReturn("ada@northlight.example");
        when(visitor.getUser()).thenReturn(user);
        JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class);
        when(workspace.getName()).thenReturn("live");
        when(visitor.getWorkspace()).thenReturn(workspace);
        when(visitor.getNode(anyString())).thenAnswer(call -> {
            String path = call.getArgument(0);
            readAttempts.add(path);
            if (unreadable.contains(path) || !(existingNodes.contains(path) || path.startsWith(BRIEFS) || path.startsWith(FILES))) {
                throw new PathNotFoundException(path);
            }
            JCRNodeWrapper node = readable.get(path);
            return node != null ? node : mock(JCRNodeWrapper.class);
        });

        catalog = new Fakes.Catalog();
        writer = new FakeWriter();
        submitters = new FakeSubmitters();
        submitters.groups.add("/groups/" + NORTHLIGHT);
        action = new CreateContentFormAction();
        action.setCatalog(catalog);
        action.setWriter(writer);
        action.setSubmitters(submitters);

        values = new HashMap<>();
        values.put("title", List.of("Summer launch film"));
        values.put("summary", List.of("A 30 second film"));
        uploads = new ArrayList<>(List.of(new SubmittedFile("media", "film.png", "image/png", new byte[]{1})));
        referer = null;
    }

    private JCRNodeWrapper folder(String path, String... children) throws Exception {
        JCRNodeWrapper folder = mock(JCRNodeWrapper.class);
        when(folder.getPath()).thenReturn(path);
        for (String child : children) {
            when(folder.hasNode(child)).thenReturn(true);
            JCRNodeWrapper sub = mock(JCRNodeWrapper.class);
            when(sub.getApplicableChildNodeDefinition(anyString(), anyString())).thenReturn(mock(ExtendedNodeDefinition.class));
            when(folder.getNode(child)).thenReturn(sub);
        }
        when(folder.getApplicableChildNodeDefinition(anyString(), anyString())).thenReturn(mock(ExtendedNodeDefinition.class));
        return folder;
    }

    private JCRNodeWrapper actionNode() throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getPath()).thenReturn("/sites/orvane/forms/supplier/actions/createContent");
        when(node.hasProperty(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            return strings.containsKey(name) || lists.containsKey(name) || references.containsKey(name) || name.equals("publish");
        });
        when(node.getProperty(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            if (references.containsKey(name)) {
                when(property.getNode()).thenReturn(references.get(name));
            } else if (name.equals("publish")) {
                when(property.getBoolean()).thenReturn(true);
            } else if (lists.containsKey(name) && !lists.get(name).isEmpty()) {
                JCRValueWrapper[] wrappers = lists.get(name).stream().map(text -> {
                    try {
                        JCRValueWrapper value = mock(JCRValueWrapper.class);
                        when(value.getString()).thenReturn(text);
                        return value;
                    } catch (RepositoryException e) {
                        throw new IllegalStateException(e);
                    }
                }).toArray(JCRValueWrapper[]::new);
                when(property.isMultiple()).thenReturn(true);
                when(property.getValues()).thenReturn(wrappers);
            } else {
                when(property.getString()).thenReturn(strings.get(name));
            }
            return property;
        });
        return node;
    }

    private FormActionContext context() throws Exception {
        actionNode = actionNode();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Referer")).thenReturn(referer);
        when(request.getServerName()).thenReturn("www.example.com");
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getDefaultLanguage()).thenReturn("en");
        when(site.getLanguagesAsLocales()).thenReturn(List.of(Locale.FRENCH, Locale.ENGLISH));
        when(form.getResolveSite()).thenReturn(site);
        return new FormActionContext(actionNode, form, request, mock(HttpServletResponse.class), visitor, Locale.ENGLISH, "orvane", values,
                Map.of(), uploads);
    }

    private FormActionException refused(int status) throws Exception {
        FormActionContext context = context();
        FormActionException error = assertThrows(FormActionException.class, () -> action.execute(context));
        assertEquals(status, error.getHttpStatus());
        assertTrue(error.hasVisitorMessage(), "the visitor reads a sentence");
        assertTrue(writer.plans.isEmpty(), "nothing was written");
        return error;
    }

    @Test
    void createsTheContentInTheSubfolderOfTheSubmittersGroup() throws Exception {
        FormActionResult result = action.execute(context());

        assertTrue(result.isEmpty(), "the form's own message and a redirect action do the rest");
        assertEquals(1, writer.plans.size());
        ContentPlan plan = writer.plans.get(0);
        assertEquals(BRIEFS + "/northlight", plan.parentPath());
        assertEquals(FILES + "/northlight", plan.filesPath());
        assertEquals("Summer launch film", plan.title());
        assertEquals(Fakes.Catalog.CONTENT_TYPE, plan.contentType());
        assertSame(user, writer.users.get(0));
        assertEquals(List.of(Locale.ENGLISH, Locale.FRENCH), plan.languages(), "the default language first");
        assertTrue(plan.publish());
    }

    private static void assertSame(Object expected, Object actual) {
        assertTrue(expected == actual);
    }

    @Test
    void theSubmittersOwnSessionReadsBothSubfoldersBeforeAnythingIsWritten() throws Exception {
        action.execute(context());
        assertTrue(readAttempts.contains(BRIEFS + "/northlight"));
        assertTrue(readAttempts.contains(FILES + "/northlight"));
    }

    @Test
    void aGuestIsRefusedBeforeAnythingElse() throws Exception {
        when(user.getUsername()).thenReturn("guest");
        when(user.getName()).thenReturn("guest");
        FormActionException error = refused(401);
        assertEquals(org.jahia.modules.formidable.content.messages.VisitorText.SIGN_IN_REQUIRED.in(Locale.ENGLISH), error.getVisitorMessage());
        assertTrue(readAttempts.isEmpty(), "no folder was even looked at");
    }

    @Test
    void aSubmissionWithoutASessionOrUserIsRefused() throws Exception {
        FormActionContext noSession = new FormActionContext(context().actionNode(), null, mock(HttpServletRequest.class), null, null,
                Locale.ENGLISH, "orvane", values, Map.of(), uploads);
        assertEquals(401, assertThrows(FormActionException.class, () -> action.execute(noSession)).getHttpStatus());
        when(visitor.getUser()).thenReturn(null);
        refused(401);
    }

    @Test
    void aSessionThatIsNotLiveIsRefused() throws Exception {
        JCRWorkspaceWrapper edit = mock(JCRWorkspaceWrapper.class);
        when(edit.getName()).thenReturn("default");
        when(visitor.getWorkspace()).thenReturn(edit);
        refused(503);
    }

    @Test
    void noGroupWithThePrefixMeansNoFolder() throws Exception {
        submitters.groups = new ArrayList<>(List.of("/groups/users", "/groups/other-supplier-x"));
        refused(403);
        assertTrue(readAttempts.isEmpty());
    }

    @Test
    void severalGroupsWithThePrefixMeanNoFolder() throws Exception {
        submitters.groups.add("/groups/orvane-supplier-harbor");
        refused(403);
    }

    @Test
    void aGroupWhoseSubfolderDoesNotExistIsRefusedAndTheFolderIsNeverCreated() throws Exception {
        submitters.groups = new ArrayList<>(List.of("/groups/orvane-supplier-newcomer"));
        refused(403);
        org.mockito.Mockito.verify(briefs, org.mockito.Mockito.never()).addNode(anyString(), anyString());
        org.mockito.Mockito.verify(briefs, org.mockito.Mockito.never()).addNode(anyString());
    }

    @Test
    void aSubfolderTheSubmitterCannotReadIsRefusedWhateverTheGroupSays() throws Exception {
        unreadable.add(BRIEFS + "/northlight");
        refused(403);
    }

    @Test
    void aFilesSubfolderTheSubmitterCannotReadIsRefusedToo() throws Exception {
        unreadable.add(FILES + "/northlight");
        refused(403);
    }

    @Test
    void withoutAGroupPrefixTheFoldersThemselvesAreChecked() throws Exception {
        strings.remove("groupPrefix");
        action.execute(context());
        ContentPlan plan = writer.plans.get(0);
        assertEquals(BRIEFS, plan.parentPath());
        assertEquals(FILES, plan.filesPath());
        assertTrue(readAttempts.contains(BRIEFS));

        writer.plans.clear();
        unreadable.add(BRIEFS);
        refused(403);
    }

    @Test
    void aCheckThatCannotBeMadeIsNotACheckThatPassed() throws Exception {
        org.mockito.Mockito.doThrow(new RepositoryException("repository down")).when(visitor).getNode(anyString());
        refused(403);
    }

    @Test
    void aContentTypeThatCannotBeCreatedInTheFolderIsAConfigurationError() throws Exception {
        JCRNodeWrapper denying = denyingFolder();
        when(briefs.getNode("northlight")).thenReturn(denying);
        refused(503);
    }

    private JCRNodeWrapper denyingFolder() throws Exception {
        JCRNodeWrapper sub = mock(JCRNodeWrapper.class);
        when(sub.getApplicableChildNodeDefinition(anyString(), anyString())).thenThrow(new ConstraintViolationException("no definition"));
        return sub;
    }

    @Test
    void aMissingParentFolderOrAnInvalidTypeIsAConfigurationError() throws Exception {
        references.remove("parentFolder");
        refused(503);
        references.put("parentFolder", briefs);
        strings.put("contentType", "not a type");
        refused(503);
        strings.remove("contentType");
        refused(503);
    }

    @Test
    void mappedFilesWithoutAFilesFolderAreAConfigurationError() throws Exception {
        references.remove("filesFolder");
        refused(503);
    }

    @Test
    void aVideoWithoutCaptionsIsRefusedWithTheAuthorsOwnSentence() throws Exception {
        uploads = new ArrayList<>(List.of(new SubmittedFile("media", "film.mp4", "video/mp4", new byte[]{1})));
        strings.put("pairRefusalMessage", "Every video needs its captions file (WebVTT).");
        FormActionException error = refused(422);
        assertEquals("Every video needs its captions file (WebVTT).", error.getVisitorMessage());
    }

    @Test
    void withoutAnOwnSentenceTheDefaultOneIsUsed() throws Exception {
        uploads = new ArrayList<>(List.of(new SubmittedFile("media", "film.mp4", "video/mp4", new byte[]{1})));
        FormActionException error = refused(422);
        assertEquals(org.jahia.modules.formidable.content.messages.VisitorText.PAIR_REFUSAL.in(Locale.ENGLISH), error.getVisitorMessage());
    }

    @Test
    void aValueThatDoesNotFitIsRefusedWithTheGenericSentenceAndTheDetailStaysInTheLog() throws Exception {
        lists.put("fieldMappings", List.of("minutes=durationMinutes"));
        values.put("minutes", List.of("two hours"));
        FormActionException error = refused(422);
        assertFalse(error.getVisitorMessage().contains("durationMinutes"), "the visitor is not told property names");
        assertTrue(error.getMessage().contains("minutes"), "the log is");
        assertFalse(error.getMessage().contains("two hours"));
    }

    @Test
    void aMappingToAnUnknownPropertyIsAConfigurationError() throws Exception {
        lists.put("fieldMappings", List.of("summary=nosuch"));
        FormActionException error = refused(503);
        assertFalse(error.getVisitorMessage().contains("nosuch"));
    }

    @Test
    void aMalformedMappingLineIsAConfigurationError() throws Exception {
        lists.put("fieldMappings", List.of("summary"));
        refused(503);
    }

    @Test
    void aFailureOfTheWriteIsRefusedWithoutDetails() throws Exception {
        writer.failure = new RepositoryException("constraint violation: secret internals");
        FormActionContext context = context();
        FormActionException error = assertThrows(FormActionException.class, () -> action.execute(context));
        assertEquals(500, error.getHttpStatus());
        assertFalse(error.getVisitorMessage().contains("secret"));
    }

    @Test
    void thePageTheFormWasSubmittedFromIsReferencedWhenTheSubmitterCanReadIt() throws Exception {
        strings.put("pageProperty", "brief");
        referer = "https://www.example.com/fr/sites/orvane/contents/briefs/summer-launch.html";
        JCRNodeWrapper page = mock(JCRNodeWrapper.class);
        when(page.isNodeType("jmix:mainResource")).thenReturn(true);
        when(page.getIdentifier()).thenReturn("brief-uuid");
        readable.put("/sites/orvane/contents/briefs/summer-launch", page);

        action.execute(context());
        var brief = writer.plans.get(0).properties().stream().filter(v -> v.shape().name().equals("brief")).findFirst();
        assertTrue(brief.isPresent());
        assertEquals(List.of(new Reference("brief-uuid")), brief.get().values());
    }

    @Test
    void aRefererThatNamesAnUnreadableOrForeignOrNonPageNodeLeavesThePropertyEmpty() throws Exception {
        strings.put("pageProperty", "brief");
        // unreadable by the submitter
        referer = "https://www.example.com/sites/orvane/home/secret.html";
        unreadable.add("/sites/orvane/home/secret");
        action.execute(context());
        assertNull(writer.plans.get(0).properties().stream().filter(v -> v.shape().name().equals("brief")).findFirst().orElse(null));
        // another site
        referer = "https://www.example.com/sites/rival/home/about.html";
        action.execute(context());
        assertFalse(readAttempts.contains("/sites/rival/home/about"));
        assertNull(writer.plans.get(1).properties().stream().filter(v -> v.shape().name().equals("brief")).findFirst().orElse(null));
        // a node that is neither a page nor a main resource
        referer = "https://www.example.com/sites/orvane/contents/briefs/loose.html";
        JCRNodeWrapper loose = mock(JCRNodeWrapper.class);
        readable.put("/sites/orvane/contents/briefs/loose", loose);
        action.execute(context());
        assertNull(writer.plans.get(2).properties().stream().filter(v -> v.shape().name().equals("brief")).findFirst().orElse(null));
        // no header at all
        referer = null;
        action.execute(context());
        assertNull(writer.plans.get(3).properties().stream().filter(v -> v.shape().name().equals("brief")).findFirst().orElse(null));
    }

    @Test
    void theSubmittersNameAndOrganizationComeFromThePlatform() throws Exception {
        strings.put("organizationProperty", "company");
        values.put("fakeUser", List.of("someone-else"));
        action.execute(context());
        var plan = writer.plans.get(0);
        assertEquals(List.of("ada@northlight.example"), plan.properties().stream().filter(v -> v.shape().name().equals("submittedBy")).findFirst().orElseThrow().values());
        assertEquals(List.of("Northlight Studio"), plan.properties().stream().filter(v -> v.shape().name().equals("company")).findFirst().orElseThrow().values());
        assertNotNull(plan);
    }

    @Test
    void theActionNeverAsksForSensitiveValuesAndIsRegisteredForItsType() {
        assertEquals("fmdbcnt:createContentAction", action.getNodeType());
        assertFalse(action.consumesSensitiveValues());
    }
}
