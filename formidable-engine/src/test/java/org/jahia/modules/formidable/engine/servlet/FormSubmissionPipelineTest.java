package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormSubmissionPipelineTest {

    @Test
    void verifyAuthenticationRejectsGuestWhenFormRequiresAuthentication() throws Exception {
        // Verifies the auth gate: a guest submission must be rejected when the form requires authentication.
        FormSubmissionPipeline pipeline = newPipelineWithFormNode(true);
        JahiaUser guestUser = mock(JahiaUser.class);
        when(guestUser.getName()).thenReturn("guest");

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyAuthentication(pipeline, guestUser));

        // Expected outcome: FMDB-009 is returned for guest submissions on protected forms.
        assertEquals(ErrorCode.FMDB_009, error.errorCode);
    }

    @Test
    void verifyAuthenticationAllowsAuthenticatedUserWhenFormRequiresAuthentication() throws Exception {
        // Verifies the positive path: a non-guest submission must pass the auth gate on protected forms.
        FormSubmissionPipeline pipeline = newPipelineWithFormNode(true);
        JahiaUser authenticatedUser = mock(JahiaUser.class);
        when(authenticatedUser.getName()).thenReturn("editor");

        // Expected outcome: no exception is raised and the pipeline can continue.
        assertDoesNotThrow(() -> invokeVerifyAuthentication(pipeline, authenticatedUser));
    }

    @Test
    void verifyAuthenticationSkipsGuestCheckWhenFormDoesNotRequireAuthentication() throws Exception {
        // Verifies the bypass case: if the mixin is absent, the auth gate must not reject the submission.
        FormSubmissionPipeline pipeline = newPipelineWithFormNode(false);

        // Expected outcome: no exception is raised because the form is public.
        assertDoesNotThrow(() -> invokeVerifyAuthentication(pipeline, null));
    }

    @Test
    void verifyAuthenticationFailsClosedWhenMixinLookupThrows() throws Exception {
        // Verifies the fail-closed path: a repository error during mixin lookup must reject the submission.
        FormSubmissionPipeline pipeline = newPipelineWithBrokenFormNode();

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyAuthentication(pipeline, null));

        // Expected outcome: FMDB-500 is returned because the authentication requirement cannot be verified.
        assertEquals(ErrorCode.FMDB_500, error.errorCode);
    }

    private static FormSubmissionPipeline newPipelineWithFormNode(boolean requiresAuthentication) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:requireAuthentication")).thenReturn(requiresAuthentication);
        setField(pipeline, "formNode", formNode);
        setField(pipeline, "formId", "test-form-id");
        return pipeline;
    }

    private static FormSubmissionPipeline newPipelineWithBrokenFormNode() throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:requireAuthentication")).thenThrow(new RepositoryException("boom"));
        setField(pipeline, "formNode", formNode);
        setField(pipeline, "formId", "test-form-id");
        return pipeline;
    }

    private static void invokeVerifyAuthentication(FormSubmissionPipeline pipeline, JahiaUser currentUser) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("verifyAuthentication");
        method.setAccessible(true);
        JahiaUser previousUser = JCRSessionFactory.getInstance().getCurrentUser();
        try {
            JCRSessionFactory.getInstance().setCurrentUser(currentUser);
            method.invoke(pipeline);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SubmissionException submissionException) {
                throw submissionException;
            }
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        } finally {
            JCRSessionFactory.getInstance().setCurrentUser(previousUser);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
