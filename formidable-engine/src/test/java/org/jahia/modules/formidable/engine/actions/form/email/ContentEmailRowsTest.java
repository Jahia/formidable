package org.jahia.modules.formidable.engine.actions.form.email;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.mail.MailMessage;
import org.jahia.services.mail.MailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentEmailRowsTest {

    private static final ContentEmailRows.OptionsReader NO_OPTIONS = field -> null;

    @Test
    void listsTheAnsweredFieldsInTheOrderOfTheFormUnderTheirTitles() throws Exception {
        JCRNodeWrapper form = form(
                field("nom", "Nom"),
                fieldset("identite", field("prenom", "Prénom"), field("courriel", "Adresse courriel")),
                field("message", "Message"));
        Map<String, List<String>> submitted = new LinkedHashMap<>();
        submitted.put("message", List.of("Bonjour"));
        submitted.put("courriel", List.of("ada@example.org"));
        submitted.put("nom", List.of("Lovelace"));
        submitted.put("prenom", List.of("Ada"));

        List<ContentEmailRows.Row> rows = ContentEmailRows.of(form, submitted, NO_OPTIONS);

        assertEquals(List.of(
                new ContentEmailRows.Row("Nom", List.of("Lovelace")),
                new ContentEmailRows.Row("Prénom", List.of("Ada")),
                new ContentEmailRows.Row("Adresse courriel", List.of("ada@example.org")),
                new ContentEmailRows.Row("Message", List.of("Bonjour"))), rows);
    }

    @Test
    void showsAChoiceByTheLabelOfItsOption() throws Exception {
        JCRNodeWrapper topics = field("sujets", "Sujets");
        JCRNodeWrapper form = form(topics);
        ContentEmailRows.OptionsReader options = field -> field == topics
                ? new String[]{
                        "{\"value\":\"budget\",\"label\":\"Budget de l'État\",\"selected\":false}",
                        "{\"value\":\"impots\",\"label\":\"\",\"selected\":false}",
                        "not json"}
                : null;

        List<ContentEmailRows.Row> rows = ContentEmailRows.of(form,
                Map.of("sujets", List.of("budget", "impots", "autre")), options);

        // A blank label, an unreadable entry and a value no option declares all show the submitted value.
        assertEquals(List.of(new ContentEmailRows.Row("Sujets", List.of("Budget de l'État", "impots", "autre"))), rows);
    }

    @Test
    void headsAFieldWithoutTitleByItsNameAndPutsUndeclaredNamesLast() throws Exception {
        JCRNodeWrapper form = form(field("nom", null), field("courriel", "Adresse courriel"));
        Map<String, List<String>> submitted = new LinkedHashMap<>();
        submitted.put("zeta", List.of("z"));
        submitted.put("courriel", List.of("ada@example.org"));
        submitted.put("alpha", List.of("a"));
        submitted.put("nom", List.of("Lovelace"));

        List<ContentEmailRows.Row> rows = ContentEmailRows.of(form, submitted, NO_OPTIONS);

        assertEquals(List.of("nom", "Adresse courriel", "alpha", "zeta"),
                rows.stream().map(ContentEmailRows.Row::heading).toList());
    }

    @Test
    void fallsBackToTheSubmittedNamesWhenTheFormCannotBeRead() throws Exception {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.getPath()).thenReturn("/form");
        when(form.hasNode(anyString())).thenThrow(new RepositoryException("unreadable"));

        List<ContentEmailRows.Row> rows = ContentEmailRows.of(form,
                Map.of("nom", List.of("Lovelace"), "courriel", List.of("ada@example.org")), NO_OPTIONS);

        assertEquals(List.of(
                new ContentEmailRows.Row("courriel", List.of("ada@example.org")),
                new ContentEmailRows.Row("nom", List.of("Lovelace"))), rows);
    }

    @Test
    void fallsBackToTheSubmittedNamesWithoutAForm() {
        List<ContentEmailRows.Row> rows = ContentEmailRows.of(null, Map.of("nom", List.of("Lovelace")), NO_OPTIONS);

        assertEquals(List.of(new ContentEmailRows.Row("nom", List.of("Lovelace"))), rows);
    }

    @Test
    void theContentEmailShowsTheRowsInItsTextAndHtmlBodies() throws Exception {
        JCRNodeWrapper form = form(field("nom", "Nom"), field("courriel", "Adresse courriel"));
        when(form.getDisplayableName()).thenReturn("Contact");
        JCRNodeWrapper actions = mock(JCRNodeWrapper.class);
        when(actions.getParent()).thenReturn(form);
        JCRNodeWrapper action = mock(JCRNodeWrapper.class);
        when(action.getParent()).thenReturn(actions);
        JCRPropertyWrapper to = mock(JCRPropertyWrapper.class);
        when(to.getString()).thenReturn("team@example.com");
        when(action.hasProperty("to")).thenReturn(true);
        when(action.getProperty("to")).thenReturn(to);
        MailService mailService = mock(MailService.class);
        when(mailService.isEnabled()).thenReturn(true);
        doNothing().when(mailService).sendMessage(any(MailMessage.class));
        ArgumentCaptor<MailMessage> sent = ArgumentCaptor.forClass(MailMessage.class);
        Map<String, List<String>> submitted = new LinkedHashMap<>();
        submitted.put("courriel", List.of("ada@example.org"));
        submitted.put("nom", List.of("Lovelace"));

        SendEmailContentFormAction emailAction = new SendEmailContentFormAction();
        emailAction.bindMailService(mailService);
        emailAction.execute(action, null, null, submitted, List.of());

        verify(mailService).sendMessage(sent.capture());
        assertEquals("Contact\n\nNom: Lovelace\nAdresse courriel: ada@example.org\n", sent.getValue().getTextBody());
        assertTrue(sent.getValue().getHtmlBody().contains(
                "<tr><th align=\"left\">Nom</th><td>Lovelace</td></tr>"
                        + "<tr><th align=\"left\">Adresse courriel</th><td>ada@example.org</td></tr>"),
                sent.getValue().getHtmlBody());
    }

    private static JCRNodeWrapper form(JCRNodeWrapper... fields) throws Exception {
        return node("form", Set.of(), null, node("fields", Set.of(), null, fields));
    }

    private static JCRNodeWrapper fieldset(String name, JCRNodeWrapper... fields) throws Exception {
        return node(name, Set.of("fmdbmix:formElement", "fmdbmix:formContainer", "fmdbmix:nonSubmittable"), "Groupe", fields);
    }

    private static JCRNodeWrapper field(String name, String title) throws Exception {
        return node(name, Set.of("fmdbmix:formElement"), title);
    }

    private static JCRNodeWrapper node(String name, Set<String> types, String title, JCRNodeWrapper... children)
            throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getPath()).thenReturn("/" + name);
        when(node.isNodeType(anyString())).thenAnswer(invocation -> types.contains(invocation.<String>getArgument(0)));
        when(node.hasProperty(anyString())).thenAnswer(invocation ->
                title != null && "jcr:title".equals(invocation.getArgument(0)));
        if (title != null) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getString()).thenReturn(title);
            when(node.getProperty("jcr:title")).thenReturn(property);
        }
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        java.util.Iterator<? extends Node> delegate = List.of(children).iterator();
        when(iterator.hasNext()).thenAnswer(invocation -> delegate.hasNext());
        when(iterator.nextNode()).thenAnswer(invocation -> delegate.next());
        when(node.getNodes()).thenReturn(iterator);
        Map<String, JCRNodeWrapper> byName = new LinkedHashMap<>();
        for (JCRNodeWrapper child : children) {
            byName.put(child.getName(), child);
        }
        when(node.hasNode(anyString())).thenAnswer(invocation -> byName.containsKey(invocation.<String>getArgument(0)));
        when(node.getNode(anyString())).thenAnswer(invocation -> byName.get(invocation.<String>getArgument(0)));
        return node;
    }
}
