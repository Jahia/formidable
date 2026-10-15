package org.jahia.modules.formidable.content.store;

import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.modules.formidable.content.convert.Reference;
import org.jahia.modules.formidable.content.convert.TypedValue;
import org.jahia.modules.formidable.content.names.ImageSize;
import org.jahia.modules.formidable.content.names.NodeNames;
import org.jahia.modules.formidable.content.plan.ContentPlan;
import org.jahia.modules.formidable.content.plan.FileGroup;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.ComplexPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link ContentWriter} on Jahia's {@link JCRTemplate} and {@link ComplexPublicationService}.
 *
 * <p><b>Why a system session.</b> The submitter is a signed-in reader of the site: they hold no write right in
 * {@code default}, so no session of theirs could create the content. This is the documented pattern for public-facing
 * writes: the session is a system session <em>attributed to the submitter</em> ({@code doExecuteWithSystemSessionAsUser}:
 * {@code jcr:createdBy} is their name, the permissions are not applied), and the action has already refused guests and
 * checked, with the submitter's own live session, that they can read the folders this writes to.</p>
 *
 * <p><b>Order.</b> Files first, each saved on its own (a name taken at that very moment is retried with a random
 * suffix), then the content node with its properties and the references to the files, in one save, so that a type with a
 * mandatory property or file reference is satisfied. Then the other languages (translated properties are written once per
 * language, in a session of that language), then the scheduling of the publication of the files and the content with their translations in the site's languages
 * (Jahia's {@code ComplexPublicationService}: asynchronous, live follows within seconds). A failure up to and including
 * the scheduling removes what was created; a failure inside the publication job is logged by the platform.</p>
 *
 * <p>Threading: a singleton with no per-request state; every session lives inside one call.</p>
 */
@Component(service = ContentWriter.class, immediate = true)
public class JcrContentWriter implements ContentWriter {

    private static final Logger log = LoggerFactory.getLogger(JcrContentWriter.class);

    private static final String DEFAULT = "default";
    private static final String LIVE = "live";
    private static final String TITLE = "jcr:title";
    private static final String TITLE_MIXIN = "mix:title";
    private static final String IMAGE_MIXIN = "jmix:image";
    private static final String FILE_TYPE = "jnt:file";
    private static final String RESOURCE_TYPE = "jnt:resource";
    private static final int NAME_ATTEMPTS = 4;

    private final Supplier<JCRTemplate> jcr;
    private ComplexPublicationService publisher;

    /** Production constructor: the platform's own services. */
    public JcrContentWriter() {
        this(JCRTemplate::getInstance);
    }

    JcrContentWriter(Supplier<JCRTemplate> jcr) {
        this.jcr = jcr;
    }

    /**
     * @param publisher the platform's publication service, the one Jahia's GraphQL {@code publish} uses
     */
    @org.osgi.service.component.annotations.Reference
    public void setPublisher(ComplexPublicationService publisher) {
        this.publisher = publisher;
    }

    /** What the first session learned, kept for the later steps and for the clean-up. */
    private static final class Progress {
        final List<String> createdIdentifiers = new ArrayList<>();
        final List<Stored> files = new ArrayList<>();
        String contentIdentifier;
        String contentPath;
    }

    private record Stored(String identifier, String path, String originalName) {
    }

    @Override
    public Created create(ContentPlan plan, JahiaUser submitter) throws RepositoryException {
        Locale first = plan.languages().isEmpty() ? Locale.ENGLISH : plan.languages().get(0);
        Progress progress = new Progress();
        try {
            jcr.get().doExecuteWithSystemSessionAsUser(submitter, DEFAULT, first, (JCRCallback<Void>) session -> {
                writeFirstLanguage(plan, session, progress);
                return null;
            });
            for (Locale locale : plan.languages().subList(Math.min(1, plan.languages().size()), plan.languages().size())) {
                jcr.get().doExecuteWithSystemSessionAsUser(submitter, DEFAULT, locale, (JCRCallback<Void>) session -> {
                    writeTranslations(plan, session, progress);
                    return null;
                });
            }
            if (plan.publish()) {
                publish(plan, progress);
            }
            List<String> filePaths = new ArrayList<>();
            progress.files.forEach(file -> filePaths.add(file.path()));
            return new Created(progress.contentIdentifier, progress.contentPath, filePaths);
        } catch (RepositoryException | RuntimeException e) {
            rollback(progress, submitter, first);
            throw e;
        }
    }

    /**
     * Schedules the publication of the content and its files, as Jahia's own GraphQL {@code publish} does: through
     * {@link ComplexPublicationService}, with the node identifiers, the languages and the whole subtree, which gives the
     * translation nodes ({@code jcr:title} and every other translated value) their way into live. The infos of
     * {@code JCRPublicationService.getPublicationInfo} do not list them, and the bare identifiers leave them out.
     *
     * <p>The session is a <em>pure</em> system session, not one attributed to the submitter: the service checks the
     * publish right of the session's user, which the submitter does not have. The call only schedules a
     * {@code PublicationJob}: live follows within a second or two, and a failure inside the job is logged by the
     * platform, where this class can no longer undo anything. It is the last step: a failure up to and including the
     * scheduling removes what was created.</p>
     */
    private void publish(ContentPlan plan, Progress progress) throws RepositoryException {
        List<String> languages = new ArrayList<>();
        plan.languages().forEach(locale -> languages.add(locale.toString()));
        List<String> ids = new ArrayList<>();
        progress.files.forEach(file -> ids.add(file.identifier()));
        ids.add(progress.contentIdentifier);
        jcr.get().doExecuteWithSystemSession((JCRCallback<Void>) session -> {
            publisher.publish(ids, languages, session, Boolean.TRUE);
            return null;
        });
    }

    private void writeFirstLanguage(ContentPlan plan, JCRSessionWrapper session, Progress progress) throws RepositoryException {
        Map<String, List<String>> storedByProperty = new LinkedHashMap<>();
        Map<String, PropertyShape> shapes = new LinkedHashMap<>();
        for (FileGroup group : plan.fileGroups()) {
            for (SubmittedFile file : group.files()) {
                Stored stored = storeFile(plan, session, file);
                progress.createdIdentifiers.add(stored.identifier());
                progress.files.add(stored);
                storedByProperty.computeIfAbsent(group.property().name(), key -> new ArrayList<>()).add(stored.identifier());
                shapes.putIfAbsent(group.property().name(), group.property());
            }
        }
        JCRNodeWrapper node = createContentNode(plan, session, storedByProperty, shapes);
        progress.contentIdentifier = node.getIdentifier();
        progress.contentPath = node.getPath();
        progress.createdIdentifiers.add(progress.contentIdentifier);
    }

    private Stored storeFile(ContentPlan plan, JCRSessionWrapper session, SubmittedFile file) throws RepositoryException {
        String base = NodeNames.fileName(file.originalName());
        for (int attempt = 0; attempt < NAME_ATTEMPTS; attempt++) {
            JCRNodeWrapper parent = session.getNode(plan.filesPath());
            String name = attempt == 0 ? NodeNames.unique(base, candidate -> hasChild(parent, candidate)) : NodeNames.randomized(base);
            try {
                JCRNodeWrapper node = parent.addNode(name, FILE_TYPE);
                JCRNodeWrapper content = node.addNode("jcr:content", RESOURCE_TYPE);
                byte[] data = file.data();
                Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(data));
                try {
                    content.setProperty("jcr:data", binary);
                } finally {
                    binary.dispose();
                }
                content.setProperty("jcr:mimeType", file.mimeType());
                content.setProperty("jcr:lastModified", Calendar.getInstance());
                if (!node.isNodeType(TITLE_MIXIN)) {
                    node.addMixin(TITLE_MIXIN);
                }
                node.setProperty(TITLE, file.originalName());
                addImageSize(node, data, file.mimeType());
                if (plan.fileMixin() != null) {
                    node.addMixin(plan.fileMixin());
                }
                for (TypedValue value : plan.fileProperties()) {
                    apply(node, value, session);
                }
                session.save();
                return new Stored(node.getIdentifier(), node.getPath(), file.originalName());
            } catch (ItemExistsException e) {
                // another submission took the name between the check and the save: nothing of this file was kept
                session.refresh(false);
            }
        }
        throw new RepositoryException("A name for the file could not be found in " + plan.filesPath());
    }

    /** {@code hasNode} as a predicate: a platform failure here is not a taken name, it fails the call (and its clean-up). */
    private static boolean hasChild(JCRNodeWrapper parent, String name) {
        try {
            return parent.hasNode(name);
        } catch (RepositoryException e) {
            throw new IllegalStateException("The folder " + parent.getPath() + " could not be inspected", e);
        }
    }

    private static void addImageSize(JCRNodeWrapper file, byte[] data, String mimeType) throws RepositoryException {
        ImageSize.of(data, mimeType).ifPresent(size -> {
            try {
                file.addMixin(IMAGE_MIXIN);
                file.setProperty("j:width", (long) size.width());
                file.setProperty("j:height", (long) size.height());
            } catch (NoSuchNodeTypeException e) {
                log.debug("[formidable-content] The mixin {} is not registered: the image is stored without its size.", IMAGE_MIXIN);
            } catch (RepositoryException e) {
                log.warn("[formidable-content] The size of an image could not be stored.", e);
            }
        });
    }

    private JCRNodeWrapper createContentNode(ContentPlan plan, JCRSessionWrapper session, Map<String, List<String>> storedByProperty,
                                             Map<String, PropertyShape> shapes) throws RepositoryException {
        String local = plan.contentType().substring(plan.contentType().indexOf(':') + 1);
        String base = plan.title() == null ? NodeNames.randomized(NodeNames.slug(local, "content")) : NodeNames.slug(plan.title(), "content");
        for (int attempt = 0; attempt < NAME_ATTEMPTS; attempt++) {
            JCRNodeWrapper parent = session.getNode(plan.parentPath());
            String name = attempt == 0 ? NodeNames.unique(base, candidate -> hasChild(parent, candidate)) : NodeNames.randomized(base);
            try {
                JCRNodeWrapper node = parent.addNode(name, plan.contentType());
                if (plan.title() != null) {
                    node.setProperty(TITLE, plan.title());
                }
                for (TypedValue value : plan.properties()) {
                    apply(node, value, session);
                }
                for (Map.Entry<String, List<String>> stored : storedByProperty.entrySet()) {
                    List<Object> references = new ArrayList<>();
                    stored.getValue().forEach(id -> references.add(new Reference(id)));
                    apply(node, new TypedValue(shapes.get(stored.getKey()), references), session);
                }
                session.save();
                return node;
            } catch (ItemExistsException e) {
                session.refresh(false);
            } catch (RepositoryException | RuntimeException e) {
                session.refresh(false);
                throw e;
            }
        }
        throw new RepositoryException("A name for the content could not be found in " + plan.parentPath());
    }

    /** The translated values, in the language of the session: the title, the translated properties, the files' titles. */
    private void writeTranslations(ContentPlan plan, JCRSessionWrapper session, Progress progress) throws RepositoryException {
        JCRNodeWrapper node = session.getNodeByIdentifier(progress.contentIdentifier);
        if (plan.title() != null) {
            node.setProperty(TITLE, plan.title());
        }
        for (TypedValue value : plan.properties()) {
            if (value.shape().i18n()) {
                apply(node, value, session);
            }
        }
        for (Stored stored : progress.files) {
            JCRNodeWrapper file = session.getNodeByIdentifier(stored.identifier());
            file.setProperty(TITLE, stored.originalName());
            for (TypedValue value : plan.fileProperties()) {
                if (value.shape().i18n()) {
                    apply(file, value, session);
                }
            }
        }
        session.save();
    }

    private static void apply(JCRNodeWrapper node, TypedValue typed, JCRSessionWrapper session) throws RepositoryException {
        PropertyShape shape = typed.shape();
        ValueFactory factory = session.getValueFactory();
        List<Value> values = new ArrayList<>();
        for (Object raw : typed.values()) {
            Value value = toValue(factory, session, shape, raw);
            if (value != null) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return;
        }
        if (shape.multiple()) {
            node.setProperty(shape.name(), values.toArray(new Value[0]));
        } else {
            node.setProperty(shape.name(), values.get(0));
        }
    }

    private static Value toValue(ValueFactory factory, JCRSessionWrapper session, PropertyShape shape, Object raw) throws RepositoryException {
        if (raw instanceof String text) {
            return factory.createValue(text);
        }
        if (raw instanceof Long number) {
            return factory.createValue(number);
        }
        if (raw instanceof Double number) {
            return factory.createValue(number);
        }
        if (raw instanceof BigDecimal number) {
            return factory.createValue(number);
        }
        if (raw instanceof Boolean flag) {
            return factory.createValue(flag);
        }
        if (raw instanceof Calendar date) {
            return factory.createValue(date);
        }
        if (raw instanceof Reference reference) {
            try {
                return factory.createValue(session.getNodeByIdentifier(reference.identifier()), shape.isWeak());
            } catch (ItemNotFoundException e) {
                // a page that is not in default (never the case for a published one): the link is left out, the content kept
                log.warn("[formidable-content] The node {} referenced by '{}' does not exist in the default workspace: the reference is left out.",
                        reference.identifier(), shape.name());
                return null;
            }
        }
        throw new RepositoryException("Unsupported value type " + raw.getClass().getName() + " for '" + shape.name() + "'");
    }

    /** Removes what the failed call created, newest first. Nothing is published yet: the publication is the last step. */
    private void rollback(Progress progress, JahiaUser submitter, Locale locale) {
        if (progress.createdIdentifiers.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(progress.createdIdentifiers);
        try {
            jcr.get().doExecuteWithSystemSessionAsUser(submitter, DEFAULT, locale, (JCRCallback<Void>) session -> {
                for (int i = ids.size() - 1; i >= 0; i--) {
                    try {
                        session.getNodeByIdentifier(ids.get(i)).remove();
                    } catch (ItemNotFoundException e) {
                        log.debug("[formidable-content] The node {} was already gone.", ids.get(i));
                    }
                }
                session.save();
                return null;
            });
        } catch (RepositoryException | RuntimeException e) {
            log.error("[formidable-content] A failed submission could not be cleaned up: remove the nodes {} by hand.", ids, e);
        }
    }
}
