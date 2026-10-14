package org.jahia.modules.formidable.accounts.choicelist;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.junit.jupiter.api.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormFieldsInitializerTest {

    private final FormFieldsInitializer initializer = new FormFieldsInitializer();

    private static JCRNodeWrapper node(String name, String title, Set<String> types, JCRNodeWrapper... children) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.getDisplayableName()).thenReturn(title);
        when(node.isNodeType(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> types.contains(call.<String>getArgument(0)));
        when(node.getNodes()).thenAnswer(call -> iterator(List.of(children)));
        return node;
    }

    private static JCRNodeIteratorWrapper iterator(List<? extends Node> nodes) {
        Iterator<? extends Node> remaining = nodes.iterator();
        JCRNodeIteratorWrapper it = mock(JCRNodeIteratorWrapper.class);
        when(it.hasNext()).thenAnswer(call -> remaining.hasNext());
        when(it.nextNode()).thenAnswer(call -> remaining.next());
        return it;
    }

    private static final Set<String> FIELD = Set.of("fmdbmix:formElement", "fmdbmix:submittableField");

    /** A form with an e-mail field, a text field, a password field, a file field, a button and a fieldset holding a field. */
    private JCRNodeWrapper actionOfAForm() throws Exception {
        JCRNodeWrapper email = node("email", "E-mail", Set.of("fmdbmix:formElement", "fmdbmix:submittableField", "fmdbmix:emailField"));
        JCRNodeWrapper text = node("name", "Name", FIELD);
        JCRNodeWrapper untitled = node("nick", "nick", FIELD);
        JCRNodeWrapper password = node("password", "Password", Set.of("fmdbmix:formElement", "fmdbmix:submittableField", "fmdbmix:sensitiveField"));
        JCRNodeWrapper file = node("cv", "CV", Set.of("fmdbmix:formElement", "fmdbmix:fileField"));
        JCRNodeWrapper button = node("send", "Send", Set.of("fmdbmix:formElement", "fmdbmix:nonSubmittable"));
        JCRNodeWrapper inner = node("phone", "Phone", FIELD);
        JCRNodeWrapper fieldset = node("more", "More", Set.of("fmdbmix:formElement", "fmdbmix:nonSubmittable", "fmdbmix:formContainer"), inner);
        JCRNodeWrapper fields = node("fields", "fields", Set.of(), email, text, untitled, password, file, button, fieldset);
        JCRNodeWrapper form = node("form", "Form", Set.of("fmdbmix:formRoot"), fields);
        JCRNodeWrapper actions = node("actions", "actions", Set.of());
        JCRNodeWrapper action = node("signup", "Sign up", Set.of());
        when(action.getParent()).thenReturn(actions);
        when(actions.getParent()).thenReturn(form);
        when(form.getParent()).thenThrow(new ItemNotFoundException("root"));
        return action;
    }

    private List<String> values(String kind, JCRNodeWrapper context) throws Exception {
        List<ChoiceListValue> values = initializer.getChoiceListValues(null, kind, List.of(), Locale.ENGLISH, Map.of("contextNode", context));
        List<String> stored = new java.util.ArrayList<>();
        for (ChoiceListValue value : values) {
            stored.add(value.getValue().getString());
        }
        return stored;
    }

    @Test
    void theEmailKindListsTheEmailFieldsOnly() throws Exception {
        // Verifies the list for the e-mail property: the fields carrying fmdbmix:emailField.
        assertEquals(List.of("email"), values("email", actionOfAForm()));
    }

    @Test
    void theSensitiveKindListsThePasswordFieldsOnly() throws Exception {
        // Verifies the list for a password property: only sensitive fields, so a plain text field cannot be picked.
        assertEquals(List.of("password"), values("sensitive", actionOfAForm()));
    }

    @Test
    void theAnyKindListsEveryOtherFieldThatSubmitsAValueFilesButtonsAndContainersExcludedNestedFieldsIncluded() throws Exception {
        // Verifies the generic list: the password is not offered, the file and the button neither, a field in a fieldset is.
        assertEquals(List.of("email", "name", "nick", "phone"), values("any", actionOfAForm()));
    }

    @Test
    void aFieldIsLabelledByItsTitleAndNameAndTheStoredValueIsTheNodeName() throws Exception {
        // Verifies what the editor shows and what is stored.
        JCRNodeWrapper action = actionOfAForm();

        List<ChoiceListValue> values = initializer.getChoiceListValues(null, "any", List.of(), Locale.ENGLISH, Map.of("contextNode", action));

        assertEquals("E-mail (email)", values.get(0).getDisplayName());
        assertEquals("email", values.get(0).getValue().getString());
        assertEquals("nick", values.get(2).getDisplayName());
    }

    @Test
    void withoutAContextNodeOrAFormTheListIsEmpty() throws Exception {
        // Verifies the degradations: creation outside a form, a context of another kind.
        assertTrue(initializer.getChoiceListValues(null, "any", List.of(), Locale.ENGLISH, Map.of()).isEmpty());
        assertTrue(initializer.getChoiceListValues(null, "any", List.of(), Locale.ENGLISH, null).isEmpty());
        JCRNodeWrapper orphan = node("orphan", "orphan", Set.of());
        when(orphan.getParent()).thenThrow(new ItemNotFoundException("root"));
        assertTrue(values("any", orphan).isEmpty());
        assertEquals("formidableAccountsFields", initializer.getKey());
    }
}
