package org.jahia.modules.formidable.content.store;

import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.modules.formidable.content.convert.Reference;
import org.jahia.modules.formidable.content.convert.TypedValue;
import org.jahia.modules.formidable.content.plan.ContentPlan;
import org.jahia.modules.formidable.content.plan.FileGroup;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.PublicationInfo;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import javax.jcr.Binary;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JcrContentWriterTest {

    private static final String BRIEFS = "/sites/orvane/contents/briefs/northlight";
    private static final String FILES = "/sites/orvane/files/briefs/northlight";

    private final Map<Locale, JCRSessionWrapper> sessions = new HashMap<>();
    private final List<Locale> opened = new ArrayList<>();
    private final Map<String, JCRNodeWrapper> byIdentifier = new HashMap<>();
    private final List<String> takenNames = new ArrayList<>();
    private final Map<String, PublicationInfo> infoByRoot = new HashMap<>();
    private final JCRSessionWrapper liveSession = mock(JCRSessionWrapper.class);

    private JCRTemplate template;
    private JCRPublicationService publication;
    private JahiaUser submitter;
    private JCRNodeWrapper briefs;
    private JCRNodeWrapper filesFolder;
    private JCRNodeWrapper fileNode;
    private JCRNodeWrapper fileContent;
    private JCRNodeWrapper contentNode;
    private ValueFactory factory;
    private JcrContentWriter writer;
    private int contentAttempts;

    @BeforeEach
    void setUp() throws Exception {
        template = mock(JCRTemplate.class);
        publication = mock(JCRPublicationService.class);
        submitter = mock(JahiaUser.class);
        factory = mock(ValueFactory.class);
        when(factory.createBinary(any())).thenReturn(mock(Binary.class));
        when(factory.createValue(anyString())).thenAnswer(call -> value(call.getArgument(0)));
        when(factory.createValue(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(call -> value(String.valueOf(call.<Long>getArgument(0))));
        when(factory.createValue(org.mockito.ArgumentMatchers.anyDouble())).thenAnswer(call -> value(String.valueOf(call.<Double>getArgument(0))));
        when(factory.createValue(anyBoolean())).thenAnswer(call -> value(String.valueOf(call.<Boolean>getArgument(0))));
        when(factory.createValue(any(java.math.BigDecimal.class))).thenAnswer(call -> value(call.<java.math.BigDecimal>getArgument(0).toString()));
        when(factory.createValue(any(java.util.Calendar.class))).thenAnswer(call -> value("date"));
        when(factory.createValue(any(javax.jcr.Node.class), anyBoolean())).thenAnswer(call -> value("ref:" + call.getArgument(1)));

        briefs = mock(JCRNodeWrapper.class);
        when(briefs.getPath()).thenReturn(BRIEFS);
        filesFolder = mock(JCRNodeWrapper.class);
        when(filesFolder.getPath()).thenReturn(FILES);
        when(filesFolder.hasNode(anyString())).thenAnswer(call -> takenNames.contains(call.getArgument(0)));

        fileNode = mock(JCRNodeWrapper.class);
        fileContent = mock(JCRNodeWrapper.class);
        when(fileNode.addNode("jcr:content", "jnt:resource")).thenReturn(fileContent);
        when(fileNode.getIdentifier()).thenReturn("file-1");
        when(fileNode.getPath()).thenReturn(FILES + "/film.png");
        byIdentifier.put("file-1", fileNode);

        contentNode = mock(JCRNodeWrapper.class);
        when(contentNode.getIdentifier()).thenReturn("content-1");
        when(contentNode.getPath()).thenReturn(BRIEFS + "/summer-launch-film");
        byIdentifier.put("content-1", contentNode);
        when(briefs.addNode(anyString(), eq("ctmd:submission"))).thenAnswer(call -> {
            contentAttempts++;
            return contentNode;
        });

        when(template.doExecuteWithSystemSessionAsUser(any(), eq("default"), any(Locale.class), any(JCRCallback.class))).thenAnswer(call -> {
            Locale locale = call.getArgument(2);
            opened.add(locale);
            return ((JCRCallback<?>) call.getArgument(3)).doInJCR(sessions.computeIfAbsent(locale, this::session));
        });
        when(template.doExecuteWithSystemSessionAsUser(any(), eq("live"), any(Locale.class), any(JCRCallback.class))).thenAnswer(call -> {
            opened.add(call.getArgument(2));
            return ((JCRCallback<?>) call.getArgument(3)).doInJCR(liveSession);
        });
        when(publication.getPublicationInfo(anyString(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(JCRSessionWrapper.class), any(JCRSessionWrapper.class)))
                .thenAnswer(call -> {
                    PublicationInfo info = mock(PublicationInfo.class);
                    infoByRoot.put(call.getArgument(0), info);
                    return List.of(info);
                });
        writer = new JcrContentWriter(() -> template, () -> publication);
    }

    private JCRSessionWrapper session(Locale locale) {
        try {
            JCRSessionWrapper session = mock(JCRSessionWrapper.class);
            when(session.getNode(BRIEFS)).thenReturn(briefs);
            when(session.getNode(FILES)).thenReturn(filesFolder);
            when(session.getValueFactory()).thenReturn(factory);
            when(session.getNodeByIdentifier(anyString())).thenAnswer(call -> {
                JCRNodeWrapper node = byIdentifier.get(call.<String>getArgument(0));
                if (node == null) {
                    throw new ItemNotFoundException(call.<String>getArgument(0));
                }
                return node;
            });
            return session;
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Value value(String text) {
        Value value = mock(Value.class);
        try {
            when(value.getString()).thenReturn(text);
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
        return value;
    }

    private static ContentPlan plan(boolean publish, List<Locale> languages, List<FileGroup> groups) {
        List<TypedValue> properties = List.of(
                new TypedValue(new PropertyShape("description", PropertyType.STRING, false, true), List.of("A 30 second film")),
                new TypedValue(new PropertyShape("durationMinutes", PropertyType.LONG, false, false), List.of(2L)),
                new TypedValue(new PropertyShape("submittedBy", PropertyType.STRING, false, false), List.of("ada@northlight.example")));
        return new ContentPlan("ctmd:submission", BRIEFS, FILES, "Summer launch film", properties, groups, "ctmdmix:credited",
                List.of(new TypedValue(new PropertyShape("ctmdCredit", PropertyType.STRING, false, true), List.of("Northlight Studio"))),
                publish, languages);
    }

    private static List<FileGroup> oneImage() {
        return List.of(new FileGroup(new PropertyShape("media", PropertyType.WEAKREFERENCE, true, false),
                List.of(new SubmittedFile("media", "Film.PNG", "image/png", new byte[]{1, 2, 3}))));
    }

    @Test
    void storesTheFileThenTheContentThenPublishesBoth() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);

        ContentWriter.Created created = writer.create(plan(true, List.of(Locale.ENGLISH), oneImage()), submitter);

        assertEquals("content-1", created.identifier());
        assertEquals(BRIEFS + "/summer-launch-film", created.path());
        assertEquals(List.of(FILES + "/film.png"), created.filePaths());

        verify(fileContent).setProperty(eq("jcr:mimeType"), eq("image/png"));
        verify(fileNode).setProperty("jcr:title", "Film.PNG");
        verify(fileNode).addMixin("ctmdmix:credited");
        verify(fileNode).setProperty(eq("ctmdCredit"), any(Value.class));
        verify(briefs).addNode("summer-launch-film", "ctmd:submission");
        verify(contentNode).setProperty("jcr:title", "Summer launch film");
        verify(contentNode).setProperty(eq("description"), any(Value.class));
        verify(contentNode).setProperty(eq("durationMinutes"), any(Value.class));
        verify(contentNode).setProperty(eq("submittedBy"), any(Value.class));
        ArgumentCaptor<Value[]> references = ArgumentCaptor.forClass(Value[].class);
        verify(contentNode).setProperty(eq("media"), references.capture());
        assertEquals(1, references.getValue().length, "the weak reference to the stored file");
        verify(factory).createValue(fileNode, true);

        InOrder order = inOrder(filesFolder, briefs, publication);
        order.verify(filesFolder).addNode("film.png", "jnt:file");
        order.verify(briefs).addNode("summer-launch-film", "ctmd:submission");
        order.verify(publication).publishByInfoList(anyList(), eq("default"), eq("live"), eq(false), isNull());
    }

    @Test
    void publishesTheInfoOfEachRootWithItsTranslationsInTheSitesLanguages() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);

        writer.create(plan(true, List.of(Locale.ENGLISH, Locale.FRENCH), oneImage()), submitter);

        // the info is collected for the file and for the content, for both languages, sub nodes and whole subtree, between a
        // system session on default and one on live (the submitter reads neither)
        java.util.Set<String> languages = java.util.Set.of("en", "fr");
        verify(publication).getPublicationInfo(eq("file-1"), eq(languages), eq(false), eq(true), eq(true), any(JCRSessionWrapper.class), eq(liveSession));
        verify(publication).getPublicationInfo(eq("content-1"), eq(languages), eq(false), eq(true), eq(true), any(JCRSessionWrapper.class), eq(liveSession));
        ArgumentCaptor<List<PublicationInfo>> infos = ArgumentCaptor.forClass(List.class);
        verify(publication).publishByInfoList(infos.capture(), eq("default"), eq("live"), eq(false), isNull());
        assertEquals(List.of(infoByRoot.get("file-1"), infoByRoot.get("content-1")), infos.getValue(), "the file first, then the content");
        // the bare identifiers are never published: that left every translated value out of live
        verify(publication, never()).publish(anyList(), anyString(), anyString(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void languagesWithARegionKeepTheirJahiaCode() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        writer.create(plan(true, List.of(Locale.ENGLISH, Locale.forLanguageTag("pt-PT")), oneImage()), submitter);
        verify(publication).getPublicationInfo(eq("content-1"), eq(java.util.Set.of("en", "pt_PT")), eq(false), eq(true), eq(true),
                any(JCRSessionWrapper.class), any(JCRSessionWrapper.class));
    }

    @Test
    void theFileIsSavedBeforeTheContentSoAMandatoryReferenceIsSatisfied() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        writer.create(plan(false, List.of(Locale.ENGLISH), oneImage()), submitter);
        JCRSessionWrapper session = sessions.get(Locale.ENGLISH);
        InOrder order = inOrder(session, briefs);
        order.verify(session).save();
        order.verify(briefs).addNode(anyString(), eq("ctmd:submission"));
        order.verify(session).save();
    }

    @Test
    void writesAreAttributedToTheSubmitterInTheDefaultWorkspace() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        writer.create(plan(false, List.of(Locale.ENGLISH), oneImage()), submitter);
        verify(template).doExecuteWithSystemSessionAsUser(eq(submitter), eq("default"), eq(Locale.ENGLISH), any(JCRCallback.class));
    }

    @Test
    void doesNotPublishWhenThePlanSaysNo() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        writer.create(plan(false, List.of(Locale.ENGLISH), oneImage()), submitter);
        verify(publication, never()).publishByInfoList(anyList(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void theTitleAndTheTranslatedValuesAreWrittenInEveryLanguage() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        writer.create(plan(false, List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN), oneImage()), submitter);

        assertEquals(List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN), opened.stream().distinct().toList(), "the default language first");
        // first language: written with the node; then once more for each other language
        verify(contentNode, times(3)).setProperty("jcr:title", "Summer launch film");
        verify(contentNode, times(3)).setProperty(eq("description"), any(Value.class));
        verify(contentNode, times(1)).setProperty(eq("durationMinutes"), any(Value.class));
        verify(contentNode, times(1)).setProperty(eq("submittedBy"), any(Value.class));
        verify(fileNode, times(3)).setProperty("jcr:title", "Film.PNG");
        verify(fileNode, times(3)).setProperty(eq("ctmdCredit"), any(Value.class));
    }

    @Test
    void aNameAlreadyTakenGetsANumericSuffix() throws Exception {
        takenNames.add("film.png");
        when(filesFolder.addNode("film-1.png", "jnt:file")).thenReturn(fileNode);
        writer.create(plan(false, List.of(Locale.ENGLISH), oneImage()), submitter);
        verify(filesFolder).addNode("film-1.png", "jnt:file");
    }

    @Test
    void aNameTakenAtTheSameInstantIsRetriedWithARandomSuffix() throws Exception {
        when(briefs.addNode(anyString(), eq("ctmd:submission"))).thenAnswer(call -> {
            contentAttempts++;
            if (contentAttempts == 1) {
                throw new ItemExistsException("taken meanwhile");
            }
            return contentNode;
        });
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        writer.create(plan(false, List.of(Locale.ENGLISH), oneImage()), submitter);
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        verify(briefs, times(2)).addNode(names.capture(), eq("ctmd:submission"));
        assertEquals("summer-launch-film", names.getAllValues().get(0));
        assertTrue(names.getAllValues().get(1).matches("summer-launch-film-[0-9a-f]{8}"), names.getAllValues().get(1));
        verify(sessions.get(Locale.ENGLISH)).refresh(false);
    }

    @Test
    void whenTheContentCannotBeSavedTheStoredFilesAreRemovedAndTheErrorReachesTheCaller() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        JCRSessionWrapper first = sessions.computeIfAbsent(Locale.ENGLISH, this::session);
        // the file's save works, the content's does not
        org.mockito.Mockito.doNothing().doThrow(new javax.jcr.nodetype.ConstraintViolationException("mandatory property missing")).when(first).save();

        assertThrows(RepositoryException.class, () -> writer.create(plan(true, List.of(Locale.ENGLISH), oneImage()), submitter));

        verify(fileNode).remove();
        verify(contentNode, never()).remove();
        verify(publication, never()).publishByInfoList(anyList(), anyString(), anyString(), anyBoolean(), any());
        verify(publication, never()).unpublish(anyList());
    }

    @Test
    void whenThePublicationFailsEverythingIsUnpublishedAndRemoved() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        org.mockito.Mockito.doThrow(new RepositoryException("publication failed")).when(publication)
                .publishByInfoList(anyList(), anyString(), anyString(), anyBoolean(), any());

        assertThrows(RepositoryException.class, () -> writer.create(plan(true, List.of(Locale.ENGLISH), oneImage()), submitter));

        verify(publication).unpublish(List.of("file-1", "content-1"));
        InOrder order = inOrder(contentNode, fileNode);
        order.verify(contentNode).remove();
        order.verify(fileNode).remove();
    }

    @Test
    void aFailureOfTheCleanUpDoesNotHideTheOriginalError() throws Exception {
        when(filesFolder.addNode("film.png", "jnt:file")).thenReturn(fileNode);
        org.mockito.Mockito.doThrow(new RepositoryException("publication failed")).when(publication)
                .publishByInfoList(anyList(), anyString(), anyString(), anyBoolean(), any());
        org.mockito.Mockito.doThrow(new RepositoryException("cannot remove")).when(contentNode).remove();
        RepositoryException error = assertThrows(RepositoryException.class,
                () -> writer.create(plan(true, List.of(Locale.ENGLISH), oneImage()), submitter));
        assertEquals("publication failed", error.getMessage());
    }

    @Test
    void aContentWithoutFilesNeedsNoFilesFolder() throws Exception {
        ContentPlan noFiles = new ContentPlan("ctmd:submission", BRIEFS, null, "Summer launch film", List.of(), List.of(), null, List.of(), false,
                List.of(Locale.ENGLISH));
        ContentWriter.Created created = writer.create(noFiles, submitter);
        assertEquals(List.of(), created.filePaths());
        verify(filesFolder, never()).addNode(anyString(), anyString());
        verify(contentNode).setProperty("jcr:title", "Summer launch film");
    }

    @Test
    void aSingleReferenceIsWrittenAsOneValueNotAnArray() throws Exception {
        ContentPlan plan = new ContentPlan("ctmd:submission", BRIEFS, null, null,
                List.of(new TypedValue(new PropertyShape("brief", PropertyType.WEAKREFERENCE, false, false), List.of(new Reference("content-1")))),
                List.of(), null, List.of(), false, List.of(Locale.ENGLISH));
        writer.create(plan, submitter);
        verify(contentNode).setProperty(eq("brief"), any(Value.class));
        verify(contentNode, never()).setProperty(eq("brief"), any(Value[].class));
        assertNotEquals(0, contentAttempts);
    }

    @Test
    void aReferenceToANodeThatIsNotInTheDefaultWorkspaceIsLeftOutAndTheContentKept() throws Exception {
        ContentPlan plan = new ContentPlan("ctmd:submission", BRIEFS, null, null,
                List.of(new TypedValue(new PropertyShape("brief", PropertyType.WEAKREFERENCE, false, false), List.of(new Reference("gone")))),
                List.of(), null, List.of(), false, List.of(Locale.ENGLISH));
        ContentWriter.Created created = writer.create(plan, submitter);
        assertEquals("content-1", created.identifier());
        verify(contentNode, never()).setProperty(eq("brief"), any(Value.class));
    }

    @Test
    void anImageGetsItsSizeAndOtherFilesDoNot() throws Exception {
        when(filesFolder.addNode(anyString(), eq("jnt:file"))).thenReturn(fileNode);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        List<FileGroup> groups = List.of(new FileGroup(new PropertyShape("media", PropertyType.WEAKREFERENCE, true, false),
                List.of(new SubmittedFile("media", "cover.png", "image/png", png.toByteArray()))));
        writer.create(plan(false, List.of(Locale.ENGLISH), groups), submitter);
        verify(fileNode).addMixin("jmix:image");
        verify(fileNode).setProperty("j:width", 40L);
        verify(fileNode).setProperty("j:height", 30L);

        org.mockito.Mockito.clearInvocations(fileNode);
        List<FileGroup> video = List.of(new FileGroup(new PropertyShape("media", PropertyType.WEAKREFERENCE, true, false),
                List.of(new SubmittedFile("media", "film.mp4", "video/mp4", new byte[]{1, 2, 3}))));
        writer.create(plan(false, List.of(Locale.ENGLISH), video), submitter);
        verify(fileNode, never()).addMixin("jmix:image");
        verify(fileNode, never()).setProperty(eq("j:width"), org.mockito.ArgumentMatchers.anyLong());
        verify(contentNode, org.mockito.Mockito.atLeastOnce()).setProperty(eq("media"), any(Value[].class));
    }
}
