package org.jahia.modules.formidable.quarantine.store;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The intake of the store against a mocked live session: the tree it writes, the hash computed once from bytes read
 * once, no file node, and the sensitive values apart from the public ones.
 */
class JcrQuarantineStoreTest {

    private static final String SITE = "formidableTestSite";
    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-11-19T18:30:00Z");
    private static final String SECRET = "s3cr3t-Pa55word";
    private static final byte[] BYTES = "A note attached to the claim.".getBytes(StandardCharsets.UTF_8);
    /** The SHA-256 of {@link #BYTES}, computed outside the module ({@code shasum -a 256}). */
    private static final String BYTES_SHA256 = "f7c11b5f5115eb9d0f4f8619af8870daf212b37948739f8534e4088a078e7ffd";

    private JCRSessionWrapper session;
    /** Every node the store created, by path, and the type it asked for. */
    private final Map<String, JCRNodeWrapper> created = new LinkedHashMap<>();
    private final Map<String, String> createdTypes = new LinkedHashMap<>();
    /** The bytes of every binary the store created. */
    private final List<byte[]> binaries = new ArrayList<>();
    private JcrQuarantineStore store;

    @BeforeEach
    void setUp() throws RepositoryException {
        session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper site = node("/sites/" + SITE);
        when(session.getNode("/sites/" + SITE)).thenReturn(site);
        ValueFactory values = mock(ValueFactory.class);
        when(session.getValueFactory()).thenReturn(values);
        when(values.createBinary(any(InputStream.class))).thenAnswer(call -> {
            binaries.add(((InputStream) call.getArgument(0)).readAllBytes());
            return mock(Binary.class);
        });
        store = new JcrQuarantineStore(Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                return callback.doInJCR(session);
            }
        };
    }

    /** A node that has no child yet, and creates each child it is asked for as another such node. */
    private JCRNodeWrapper node(String path) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getPath()).thenReturn(path);
        when(node.addNode(anyString(), anyString())).thenAnswer(call -> {
            String childPath = path + "/" + call.getArgument(0);
            JCRNodeWrapper child = node(childPath);
            created.put(childPath, child);
            createdTypes.put(childPath, call.getArgument(1));
            return child;
        });
        return node;
    }

    private static HeldSubmission submission(List<SubmittedFile> files) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("fullName", List.of("Ada"));
        values.put("topics", List.of("claims", "refunds"));
        return new HeldSubmission(FORM_ID, SITE, Locale.ENGLISH, values, Map.of("password", List.of(SECRET)), files,
                new SubmissionOrigin("en", "https://example.org/form", "Europe/Paris"));
    }

    private static SubmittedFile file() {
        return new SubmittedFile("supportingDocument", "claim-note.txt", "text/plain", BYTES);
    }

    @Test
    void theHashIsTheSha256OfTheBytes() {
        assertEquals(BYTES_SHA256, Sha256.hex(BYTES));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.hex("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void theSubmissionIsWrittenUnderTheRootOfTheSiteTheFormAndTheDate() throws Exception {
        String path = store.hold(submission(List.of(file())));

        String day = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/";
        assertTrue(path.startsWith(day + "held-"), path);
        assertEquals(QuarantineTree.ROOT_TYPE, createdTypes.get("/sites/" + SITE + "/formidable-quarantine"));
        assertEquals(QuarantineTree.FORM_TYPE, createdTypes.get("/sites/" + SITE + "/formidable-quarantine/" + FORM_ID));
        assertEquals(QuarantineTree.DATE_FOLDER_TYPE, createdTypes.get(day.substring(0, day.length() - 1)));
        assertEquals(QuarantineTree.HELD_TYPE, createdTypes.get(path));
        assertEquals(QuarantineTree.ITEM_TYPE, createdTypes.get(path + "/item0"));
        JCRNodeWrapper held = created.get(path);
        verify(held).setProperty(QuarantineTree.STATE, HeldStates.HELD);
        verify(held).setProperty(QuarantineTree.FORM_ID, FORM_ID);
        verify(held).setProperty(QuarantineTree.SITE_KEY, SITE);
        verify(held).setProperty(QuarantineTree.VALUE_FIELDS, new String[]{"fullName", "topics"});
        verify(held).setProperty(QuarantineTree.SENSITIVE_FIELDS, new String[]{"password"});
        // the root on first use, then the held submission
        verify(session, times(2)).save();
    }

    @Test
    void theRootCutsItsAclInheritanceAndIsGivenNoRole() throws Exception {
        store.hold(submission(List.of(file())));

        JCRNodeWrapper root = created.get("/sites/" + SITE + "/formidable-quarantine");
        verify(root).setAclInheritanceBreak(true);
        verify(root, never()).grantRoles(anyString(), any());
        verify(root, never()).changeRoles(anyString(), any());
    }

    /**
     * A node at the path of the root, of the given type. A null {@code inherit} gives it no {@code j:acl}; otherwise
     * its {@code j:acl} carries {@code j:inherit} with that value, and the given access control entries.
     */
    private JCRNodeWrapper rootNode(String type, Boolean inherit, JCRNodeWrapper... aces) throws RepositoryException {
        String path = "/sites/" + SITE + "/formidable-quarantine";
        JCRNodeWrapper root = node(path);
        when(root.isNodeType(type)).thenReturn(true);
        if (inherit != null) {
            JCRNodeWrapper acl = node(path + "/j:acl");
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getBoolean()).thenReturn(inherit);
            when(acl.hasProperty("j:inherit")).thenReturn(true);
            when(acl.getProperty("j:inherit")).thenReturn(property);
            JCRNodeIteratorWrapper children = mock(JCRNodeIteratorWrapper.class);
            when(children.iterator()).thenReturn(List.of(aces).iterator());
            when(acl.getNodes()).thenReturn(children);
            when(root.hasNode("j:acl")).thenReturn(true);
            when(root.getNode("j:acl")).thenReturn(acl);
        }
        return root;
    }

    /** The site already holds this node at the path of the root. */
    private JCRNodeWrapper existingRoot(JCRNodeWrapper root) throws RepositoryException {
        JCRNodeWrapper site = session.getNode("/sites/" + SITE);
        when(site.hasNode(QuarantineTree.ROOT_NAME)).thenReturn(true);
        when(site.getNode(QuarantineTree.ROOT_NAME)).thenReturn(root);
        return site;
    }

    @Test
    void anExistingRootThatCutsItsInheritanceAndHoldsNoAceIsReusedAsItIs() throws Exception {
        JCRNodeWrapper existing = rootNode(QuarantineTree.ROOT_TYPE, false);
        JCRNodeWrapper site = existingRoot(existing);

        String path = store.hold(submission(List.of(file())));

        assertTrue(path.startsWith("/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/"), path);
        verify(site, never()).addNode(anyString(), anyString());
        verify(existing, never()).setAclInheritanceBreak(anyBoolean());
        verify(existing, never()).grantRoles(anyString(), any());
        verify(existing, never()).changeRoles(anyString(), any());
    }

    /** The hold of a submission is refused, and nothing is written under the root or saved. */
    private void assertRefused(JCRNodeWrapper existing) throws RepositoryException {
        existingRoot(existing);

        assertThrows(QuarantineStoreException.class, () -> store.hold(submission(List.of(file()))));
        assertTrue(created.isEmpty(), "nodes were written under a refused root: " + created.keySet());
        verify(session, never()).save();
        verify(existing, never()).setAclInheritanceBreak(anyBoolean());
    }

    @Test
    void anExistingRootOfAnotherTypeIsRefused() throws Exception {
        assertRefused(rootNode("jnt:contentFolder", false));
    }

    @Test
    void anExistingRootThatInheritsItsAccessIsRefused() throws Exception {
        assertRefused(rootNode(QuarantineTree.ROOT_TYPE, true));
    }

    @Test
    void anExistingRootWithNoAclInheritsItsAccessAndIsRefused() throws Exception {
        assertRefused(rootNode(QuarantineTree.ROOT_TYPE, null));
    }

    @Test
    void anExistingRootThatHoldsAnAccessControlEntryIsRefused() throws Exception {
        JCRNodeWrapper ace = node("/sites/" + SITE + "/formidable-quarantine/j:acl/GRANT_u_editor");

        assertRefused(rootNode(QuarantineTree.ROOT_TYPE, false, ace));
    }

    @Test
    void aRootThatAConcurrentIntakeCreatedFirstIsReadAgain() throws Exception {
        JCRNodeWrapper site = session.getNode("/sites/" + SITE);
        JCRNodeWrapper concurrent = rootNode(QuarantineTree.ROOT_TYPE, false);
        // no root before the save; the concurrent intake saved it in between
        when(site.hasNode(QuarantineTree.ROOT_NAME)).thenReturn(false, true);
        when(site.getNode(QuarantineTree.ROOT_NAME)).thenReturn(concurrent);
        doThrow(new RepositoryException("the root exists")).when(session).save();

        assertSame(concurrent, JcrQuarantineStore.rootOf(session, SITE));
        verify(session).refresh(false);
    }

    @Test
    void aRootThatAppearedBetweenTheReadAndTheSaveIsRefusedWhenItInheritsItsAccess() throws Exception {
        JCRNodeWrapper site = session.getNode("/sites/" + SITE);
        JCRNodeWrapper other = rootNode(QuarantineTree.ROOT_TYPE, true);
        when(site.hasNode(QuarantineTree.ROOT_NAME)).thenReturn(false, true);
        when(site.getNode(QuarantineTree.ROOT_NAME)).thenReturn(other);
        doThrow(new RepositoryException("the root exists")).when(session).save();

        assertThrows(JcrQuarantineStore.RefusedRootException.class, () -> JcrQuarantineStore.rootOf(session, SITE));
        verify(session).refresh(false);
    }

    @Test
    void anExistingRootWhoseAclNamesNoInheritanceIsRefused() throws Exception {
        JCRNodeWrapper existing = rootNode(QuarantineTree.ROOT_TYPE, false);
        JCRNodeWrapper acl = existing.getNode("j:acl");
        when(acl.hasProperty("j:inherit")).thenReturn(false);

        assertRefused(existing);
    }

    @Test
    void aRefusedRootIsNotWrittenAgain() throws Exception {
        existingRoot(rootNode(QuarantineTree.ROOT_TYPE, true));
        int[] calls = new int[1];
        JcrQuarantineStore counting = new JcrQuarantineStore(Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                calls[0]++;
                return callback.doInJCR(session);
            }
        };

        assertThrows(QuarantineStoreException.class, () -> counting.hold(submission(List.of(file()))));
        assertEquals(1, calls[0]);
    }

    /** A folder that already exists under {@code parent}, of the given type, with its own {@code j:acl} or not. */
    private JCRNodeWrapper existingFolder(JCRNodeWrapper parent, String name, String type, boolean ownAcl)
            throws RepositoryException {
        JCRNodeWrapper folder = node(parent.getPath() + "/" + name);
        when(folder.isNodeType(type)).thenReturn(true);
        when(folder.hasNode("j:acl")).thenReturn(ownAcl);
        when(parent.hasNode(name)).thenReturn(true);
        when(parent.getNode(name)).thenReturn(folder);
        return folder;
    }

    @Test
    void aFormFolderThatCarriesItsOwnAclIsRefused() throws Exception {
        JCRNodeWrapper root = rootNode(QuarantineTree.ROOT_TYPE, false);
        existingFolder(root, FORM_ID, QuarantineTree.FORM_TYPE, true);

        assertRefused(root);
    }

    @Test
    void aFormFolderOfAnotherTypeIsRefused() throws Exception {
        JCRNodeWrapper root = rootNode(QuarantineTree.ROOT_TYPE, false);
        existingFolder(root, FORM_ID, "jnt:contentFolder", false);

        assertRefused(root);
    }

    @Test
    void aDateFolderThatCarriesItsOwnAclIsRefused() throws Exception {
        JCRNodeWrapper root = rootNode(QuarantineTree.ROOT_TYPE, false);
        JCRNodeWrapper form = existingFolder(root, FORM_ID, QuarantineTree.FORM_TYPE, false);
        existingFolder(form, "2026", QuarantineTree.DATE_FOLDER_TYPE, true);

        assertRefused(root);
    }

    @Test
    void existingFoldersOfTheStoreWithNoAclOfTheirOwnAreReused() throws Exception {
        JCRNodeWrapper root = rootNode(QuarantineTree.ROOT_TYPE, false);
        JCRNodeWrapper form = existingFolder(root, FORM_ID, QuarantineTree.FORM_TYPE, false);
        JCRNodeWrapper year = existingFolder(form, "2026", QuarantineTree.DATE_FOLDER_TYPE, false);
        existingRoot(root);

        String path = store.hold(submission(List.of(file())));

        assertTrue(path.startsWith(year.getPath() + "/11/19/held-"), path);
        verify(form, never()).addNode(anyString(), anyString());
    }

    @Test
    void aFirstWriteThatFailsIsWrittenAgain() throws Exception {
        int[] calls = new int[1];
        JcrQuarantineStore retrying = new JcrQuarantineStore(Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                calls[0]++;
                if (calls[0] == 1) {
                    throw new RepositoryException("a concurrent intake created the day folder");
                }
                return callback.doInJCR(session);
            }
        };

        String path = retrying.hold(submission(List.of(file())));

        assertEquals(2, calls[0]);
        assertTrue(path.contains("/held-"), path);
    }

    @Test
    void aSecondWriteThatFailsRefusesTheIntake() {
        JcrQuarantineStore failing = new JcrQuarantineStore(Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
                throw new RepositoryException("the repository refuses every write");
            }
        };

        assertThrows(QuarantineStoreException.class, () -> failing.hold(submission(List.of(file()))));
    }

    @Test
    void eachFileIsReadOnceAndItsHashComputedFromThoseBytes() throws Exception {
        SubmittedFile file = spy(file());

        String path = store.hold(submission(List.of(file)));

        verify(file, times(1)).data();
        JCRNodeWrapper item = created.get(path + "/item0");
        verify(item).setProperty(QuarantineTree.SHA256, BYTES_SHA256);
        verify(item).setProperty(QuarantineTree.SIZE, (long) BYTES.length);
        verify(item).setProperty(QuarantineTree.FILE_NAME, "claim-note.txt");
        verify(item).setProperty(QuarantineTree.FIELD_NAME, "supportingDocument");
        verify(item).setProperty(QuarantineTree.MIME_TYPE, "text/plain");
        verify(item).setProperty(eq(QuarantineTree.DATA), any(Binary.class));
        assertEquals(1, binaries.size());
        assertArrayEquals(BYTES, binaries.get(0));
    }

    @Test
    void twoFilesMakeTwoItemsInTheOrderOfTheSubmission() throws Exception {
        SubmittedFile second = new SubmittedFile("supportingDocument", "second.txt", "text/plain",
                "another".getBytes(StandardCharsets.UTF_8));

        String path = store.hold(submission(List.of(file(), second)));

        verify(created.get(path + "/item0")).setProperty(QuarantineTree.FILE_NAME, "claim-note.txt");
        verify(created.get(path + "/item1")).setProperty(QuarantineTree.FILE_NAME, "second.txt");
    }

    @Test
    void noNodeOfTheTreeIsAFileAndNoPropertyIsJcrData() throws Exception {
        store.hold(submission(List.of(file())));

        for (String type : createdTypes.values()) {
            assertTrue(type.startsWith("fmdbq:"), "a node of the type " + type + " was created");
        }
        for (JCRNodeWrapper node : created.values()) {
            verify(node, never()).setProperty(eq("jcr:data"), any(Binary.class));
            verify(node, never()).uploadFile(anyString(), any(), anyString());
        }
        assertFalse(createdTypes.containsValue("jnt:file"));
        assertFalse(createdTypes.containsValue("jnt:resource"));
    }

    @Test
    void theSensitiveValuesAreWrittenApartFromThePublicOnes() throws Exception {
        String path = store.hold(submission(List.of(file())));

        JCRNodeWrapper values = created.get(path + "/" + QuarantineTree.VALUES);
        JCRNodeWrapper sensitive = created.get(path + "/" + QuarantineTree.SENSITIVE);
        assertEquals(QuarantineTree.VALUES_TYPE, createdTypes.get(path + "/" + QuarantineTree.VALUES));
        assertEquals(QuarantineTree.SENSITIVE_VALUES_TYPE, createdTypes.get(path + "/" + QuarantineTree.SENSITIVE));
        verify(values).setProperty("fullName", new String[]{"Ada"});
        verify(values).setProperty("topics", new String[]{"claims", "refunds"});
        verify(sensitive).setProperty("password", new String[]{SECRET});
        for (Map.Entry<String, JCRNodeWrapper> node : created.entrySet()) {
            if (node.getValue() != sensitive) {
                assertFalse(receivedTheSecret(node.getValue()), "the sensitive value reached " + node.getKey());
            }
        }
    }

    private static boolean receivedTheSecret(JCRNodeWrapper node) {
        for (Invocation call : mockingDetails(node).getInvocations()) {
            for (Object argument : call.getArguments()) {
                boolean secret = SECRET.equals(argument)
                        || argument instanceof String[] strings && Arrays.asList(strings).contains(SECRET);
                if (secret) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void aSubmissionThatNamesNoSiteIsRefusedBeforeAnyWrite() {
        HeldSubmission noSite = new HeldSubmission(FORM_ID, "", Locale.ENGLISH, Map.of(), Map.of(), List.of(file()),
                new SubmissionOrigin(null, null, null));

        assertThrows(QuarantineStoreException.class, () -> store.hold(noSite));
        assertTrue(created.isEmpty());
    }

    @Test
    void aStateOutsideTheSixIsRefused() {
        assertThrows(QuarantineStoreException.class, () -> store.setState("/sites/x/held", "released"));
    }

    @Test
    void aPathThatIsNotAHeldSubmissionIsRefused() throws RepositoryException {
        JCRNodeWrapper page = node("/sites/" + SITE + "/home");
        when(session.nodeExists("/sites/" + SITE + "/home")).thenReturn(true);
        when(session.getNode("/sites/" + SITE + "/home")).thenReturn(page);

        assertThrows(QuarantineStoreException.class, () -> store.setState("/sites/" + SITE + "/home", HeldStates.CHECKING));
        assertThrows(QuarantineStoreException.class, () -> store.finish("/users/root", HeldStates.REJECTED));
        verify(page, never()).setProperty(anyString(), anyString());
    }

    private static void string(JCRNodeWrapper node, String name, String value) throws RepositoryException {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getString()).thenReturn(value);
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
    }

    private static void strings(JCRNodeWrapper node, String name, String... strings) throws RepositoryException {
        JCRValueWrapper[] values = new JCRValueWrapper[strings.length];
        for (int index = 0; index < strings.length; index++) {
            JCRValueWrapper value = mock(JCRValueWrapper.class);
            when(value.getString()).thenReturn(strings[index]);
            values[index] = value;
        }
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getValues()).thenReturn(values);
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
    }

    @Test
    void aHeldSubmissionIsReadBackWithItsValuesInOrderItsSensitiveValuesAndItsFiles() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-2";
        JCRNodeWrapper held = node(path);
        when(session.nodeExists(path)).thenReturn(true);
        when(session.getNode(path)).thenReturn(held);
        when(held.isNodeType(QuarantineTree.HELD_TYPE)).thenReturn(true);
        string(held, QuarantineTree.FORM_ID, FORM_ID);
        string(held, QuarantineTree.SITE_KEY, SITE);
        string(held, QuarantineTree.LOCALE, "fr-FR");
        string(held, QuarantineTree.ORIGIN_REFERER, "https://example.org/form");
        strings(held, QuarantineTree.VALUE_FIELDS, "topics", "fullName");
        strings(held, QuarantineTree.SENSITIVE_FIELDS, "password");
        JCRNodeWrapper values = node(path + "/values");
        strings(values, "fullName", "Ada");
        strings(values, "topics", "claims", "refunds");
        when(held.hasNode(QuarantineTree.VALUES)).thenReturn(true);
        when(held.getNode(QuarantineTree.VALUES)).thenReturn(values);
        JCRNodeWrapper sensitive = node(path + "/sensitive");
        strings(sensitive, "password", SECRET);
        when(held.hasNode(QuarantineTree.SENSITIVE)).thenReturn(true);
        when(held.getNode(QuarantineTree.SENSITIVE)).thenReturn(sensitive);
        JCRNodeWrapper item = node(path + "/item0");
        when(item.getName()).thenReturn("item0");
        when(item.isNodeType(QuarantineTree.ITEM_TYPE)).thenReturn(true);
        string(item, QuarantineTree.FIELD_NAME, "supportingDocument");
        string(item, QuarantineTree.FILE_NAME, "claim-note.txt");
        string(item, QuarantineTree.MIME_TYPE, "text/plain");
        Binary binary = mock(Binary.class);
        when(binary.getStream()).thenReturn(new ByteArrayInputStream(BYTES));
        JCRPropertyWrapper data = mock(JCRPropertyWrapper.class);
        when(data.getBinary()).thenReturn(binary);
        when(item.hasProperty(QuarantineTree.DATA)).thenReturn(true);
        when(item.getProperty(QuarantineTree.DATA)).thenReturn(data);
        JCRNodeIteratorWrapper children = mock(JCRNodeIteratorWrapper.class);
        when(children.iterator()).thenReturn(List.of(values, sensitive, item).iterator());
        when(held.getNodes()).thenReturn(children);

        HeldSubmission read = store.read(path).orElseThrow();

        assertEquals(FORM_ID, read.formId());
        assertEquals(SITE, read.siteKey());
        assertEquals(Locale.forLanguageTag("fr-FR"), read.locale());
        assertEquals(List.of("topics", "fullName"), List.copyOf(read.parameters().keySet()));
        assertEquals(List.of("claims", "refunds"), read.parameters().get("topics"));
        assertEquals(Map.of("password", List.of(SECRET)), read.sensitiveParameters());
        assertEquals(List.of(file()), read.files());
        assertEquals("https://example.org/form", read.origin().referer());
    }

    @Test
    void aPathWithNoHeldSubmissionReadsAsEmpty() throws Exception {
        assertTrue(store.read("/sites/" + SITE + "/formidable-quarantine/nothing").isEmpty());
        assertTrue(store.heldRecord("/sites/" + SITE + "/formidable-quarantine/nothing").isEmpty());
    }

    @Test
    void thePublicValuesAreReadInTheOrderOfTheFieldsAndTheSensitiveNodeIsNeverRead() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-6";
        JCRNodeWrapper held = heldAt(path);
        strings(held, QuarantineTree.VALUE_FIELDS, "topics", "fullName");
        JCRNodeWrapper values = node(path + "/values");
        strings(values, "fullName", "Ada");
        strings(values, "topics", "claims", "refunds");
        when(held.hasNode(QuarantineTree.VALUES)).thenReturn(true);
        when(held.getNode(QuarantineTree.VALUES)).thenReturn(values);

        Map<String, List<String>> read = JcrQuarantineStore.publicValuesOf(held);

        assertEquals(List.of("topics", "fullName"), List.copyOf(read.keySet()));
        assertEquals(List.of("claims", "refunds"), read.get("topics"));
        verify(held, never()).getNode(QuarantineTree.SENSITIVE);
        when(held.hasNode(QuarantineTree.VALUES)).thenReturn(false);
        assertTrue(JcrQuarantineStore.publicValuesOf(held).isEmpty(), "values deleted");
    }

    /** A held submission at the path, which the session finds. */
    private JCRNodeWrapper heldAt(String path) throws RepositoryException {
        JCRNodeWrapper held = node(path);
        when(session.nodeExists(path)).thenReturn(true);
        when(session.getNode(path)).thenReturn(held);
        when(held.isNodeType(QuarantineTree.HELD_TYPE)).thenReturn(true);
        return held;
    }

    @Test
    void theAttemptsAndTheNextAttemptAreSetAndANullNextAttemptRemovesIt() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-3";
        JCRNodeWrapper held = heldAt(path);
        JCRPropertyWrapper next = mock(JCRPropertyWrapper.class);
        when(held.hasProperty(QuarantineTree.NEXT_ATTEMPT)).thenReturn(true);
        when(held.getProperty(QuarantineTree.NEXT_ATTEMPT)).thenReturn(next);

        store.setAttempts(path, 2, NOW);
        verify(held).setProperty(QuarantineTree.ATTEMPTS, 2L);
        verify(held).setProperty(eq(QuarantineTree.NEXT_ATTEMPT), any(Calendar.class));

        store.setAttempts(path, 3, null);
        verify(held).setProperty(QuarantineTree.ATTEMPTS, 3L);
        verify(next).remove();
        verify(session, times(2)).save();
    }

    @Test
    void theCompletedActionsAreSet() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-4";
        JCRNodeWrapper held = heldAt(path);

        store.setCompletedActions(path, 1);

        verify(held).setProperty(QuarantineTree.COMPLETED_ACTIONS, 1L);
        verify(session).save();
    }

    @Test
    void theRecordOfAHeldSubmissionHoldsItsStateItsFieldNamesAndItsItemsButNoValue() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-5";
        JCRNodeWrapper held = heldAt(path);
        string(held, QuarantineTree.FORM_ID, FORM_ID);
        string(held, QuarantineTree.STATE, HeldStates.HELD);
        strings(held, QuarantineTree.VALUE_FIELDS, "fullName");
        strings(held, QuarantineTree.SENSITIVE_FIELDS, "password");
        when(held.hasNode(QuarantineTree.VALUES)).thenReturn(true);
        JCRNodeWrapper item = node(path + "/item0");
        when(item.getName()).thenReturn("item0");
        when(item.isNodeType(QuarantineTree.ITEM_TYPE)).thenReturn(true);
        string(item, QuarantineTree.SHA256, BYTES_SHA256);
        JCRNodeIteratorWrapper children = mock(JCRNodeIteratorWrapper.class);
        when(children.iterator()).thenReturn(List.of(item).iterator());
        when(held.getNodes()).thenReturn(children);

        HeldRecord heldRecord = store.heldRecord(path).orElseThrow();

        assertEquals(path, heldRecord.path());
        assertEquals(HeldStates.HELD, heldRecord.state());
        assertEquals(List.of("fullName"), heldRecord.valueFields());
        assertEquals(List.of("password"), heldRecord.sensitiveFields());
        assertTrue(heldRecord.valuesPresent());
        assertEquals(1, heldRecord.items().size());
        assertEquals(BYTES_SHA256, heldRecord.items().get(0).sha256());
        assertFalse(heldRecord.items().get(0).binaryPresent());
    }

    @Test
    void deletingTheValuesRemovesBothValueNodesAndTheBytesOfEachItem() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-1";
        JCRNodeWrapper held = node(path);
        when(session.nodeExists(path)).thenReturn(true);
        when(session.getNode(path)).thenReturn(held);
        when(held.isNodeType(QuarantineTree.HELD_TYPE)).thenReturn(true);
        JCRNodeWrapper values = node(path + "/values");
        JCRNodeWrapper sensitive = node(path + "/sensitive");
        when(held.hasNode(QuarantineTree.VALUES)).thenReturn(true);
        when(held.getNode(QuarantineTree.VALUES)).thenReturn(values);
        when(held.hasNode(QuarantineTree.SENSITIVE)).thenReturn(true);
        when(held.getNode(QuarantineTree.SENSITIVE)).thenReturn(sensitive);
        JCRNodeWrapper item = node(path + "/item0");
        when(item.getName()).thenReturn("item0");
        when(item.isNodeType(QuarantineTree.ITEM_TYPE)).thenReturn(true);
        when(item.hasProperty(QuarantineTree.DATA)).thenReturn(true);
        JCRPropertyWrapper data = mock(JCRPropertyWrapper.class);
        when(item.getProperty(QuarantineTree.DATA)).thenReturn(data);
        JCRNodeIteratorWrapper children = mock(JCRNodeIteratorWrapper.class);
        when(children.iterator()).thenReturn(List.of(values, sensitive, item).iterator());
        when(held.getNodes()).thenReturn(children);

        store.finish(path, HeldStates.COMPLETED);

        verify(values).remove();
        verify(sensitive).remove();
        verify(data).remove();
        verify(held).setProperty(QuarantineTree.STATE, HeldStates.COMPLETED);
        verify(held, never()).remove();
        // the state and the deletion in one save
        verify(session, times(1)).save();
    }

    @Test
    void finishingRefusesAStateThatIsNotFinalBeforeAnyRead() throws Exception {
        assertThrows(QuarantineStoreException.class,
                () -> store.finish("/sites/" + SITE + "/held", HeldStates.RELEASING));
        assertThrows(QuarantineStoreException.class,
                () -> store.finish("/sites/" + SITE + "/held", HeldStates.NEEDS_REVIEW));
        verify(session, never()).getNode(anyString());
        verify(session, never()).save();
    }

    @Test
    void aMoveOfTheStateWritesItsTimeAsTheLastChange() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-1";
        JCRNodeWrapper held = heldAt(path);

        store.setState(path, HeldStates.REJECTED);

        verify(held).setProperty(QuarantineTree.STATE, HeldStates.REJECTED);
        verify(held).setProperty(eq(QuarantineTree.CHANGED_AT),
                argThat((Calendar at) -> at.toInstant().equals(NOW)));
        verify(session).save();
    }

    @Test
    void aFinalStateWritesItsTimeAsTheLastChangeInTheSameSave() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-1";
        JCRNodeWrapper held = heldAt(path);
        JCRNodeIteratorWrapper children = mock(JCRNodeIteratorWrapper.class);
        when(children.iterator()).thenReturn(List.<JCRNodeWrapper>of().iterator());
        when(held.getNodes()).thenReturn(children);

        store.finish(path, HeldStates.REJECTED);

        // the retention reads changedAt: a rejection by the run or by a manager starts its period
        verify(held).setProperty(QuarantineTree.STATE, HeldStates.REJECTED);
        verify(held).setProperty(eq(QuarantineTree.CHANGED_AT),
                argThat((Calendar at) -> at.toInstant().equals(NOW)));
        verify(session, times(1)).save();
    }

    @Test
    void aPurgeRemovesTheWholeHeldSubmissionAndRefusesAnyOtherNode() throws Exception {
        String path = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-1";
        JCRNodeWrapper held = heldAt(path);
        JCRNodeWrapper other = mock(JCRNodeWrapper.class);
        String otherPath = "/sites/" + SITE + "/home";
        when(session.nodeExists(otherPath)).thenReturn(true);
        when(session.getNode(otherPath)).thenReturn(other);

        store.purge(path);
        assertThrows(QuarantineStoreException.class, () -> store.purge(otherPath));

        verify(held).remove();
        verify(other, never()).remove();
        verify(session, times(1)).save();
    }
}
