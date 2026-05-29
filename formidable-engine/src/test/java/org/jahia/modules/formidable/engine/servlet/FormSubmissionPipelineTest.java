package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRTemplate;
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
import static org.mockito.Mockito.verify;
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
    void verifyAuthenticationUsesEngineOwnedSemanticMixin() throws Exception {
        // Verifies the ownership split: the pipeline must read fmdbmix:authenticatedOnlyForm
        // instead of the elements-owned wrapper mixin applied by authors.
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:authenticatedOnlyForm")).thenReturn(false);
        setField(pipeline, "formNode", formNode);
        setField(pipeline, "formId", "test-form-id");

        invokeVerifyAuthentication(pipeline, null);

        // Expected outcome: fmdbmix:authenticatedOnlyForm is consulted directly by the pipeline.
        verify(formNode).isNodeType("fmdbmix:authenticatedOnlyForm");
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

    @Test
    void resolveActionNodesRejectsSubmissionWhenSystemReadFails() throws Exception {
        // Verifies the action-list gate: if the system session cannot read the configured actions,
        // the submission must fail instead of silently continuing with an empty pipeline.
        JCRTemplate template = mock(JCRTemplate.class);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                mock(FormidableConfigService.class),
                List.<FormAction>of(),
                FormFieldMetadataCollector::collect,
                () -> template
        );
        setField(pipeline, "formId", "test-form-id");

        when(template.doExecuteWithSystemSessionAsUser(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("live"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RepositoryException("boom"));

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeResolveActionNodes(pipeline));

        // Expected outcome: FMDB-008 is returned so the client can retry instead of losing the action execution.
        assertEquals(ErrorCode.FMDB_008, error.errorCode);
    }

    @Test
    void collectFormFieldInfoRejectsSubmissionWhenMetadataCollectionFails() throws Exception {
        // Verifies the metadata gate: if JCR field metadata cannot be collected reliably,
        // the submission must fail instead of parsing against partial metadata.
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                mock(FormidableConfigService.class),
                List.<FormAction>of(),
                (formId, locale) -> { throw new RepositoryException("boom"); },
                JCRTemplate::getInstance
        );
        setField(pipeline, "formId", "test-form-id");
        setField(pipeline, "locale", java.util.Locale.ENGLISH);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeCollectFormFieldInfo(pipeline));

        // Expected outcome: FMDB-500 is returned because the submission cannot rely on partial metadata.
        assertEquals(ErrorCode.FMDB_500, error.errorCode);
    }

    private static FormSubmissionPipeline newPipelineWithFormNode(boolean requiresAuthentication) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:authenticatedOnlyForm")).thenReturn(requiresAuthentication);
        setField(pipeline, "formNode", formNode);
        setField(pipeline, "formId", "test-form-id");
        return pipeline;
    }

    private static FormSubmissionPipeline newPipelineWithBrokenFormNode() throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:authenticatedOnlyForm")).thenThrow(new RepositoryException("boom"));
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

    private static void invokeResolveActionNodes(FormSubmissionPipeline pipeline) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("resolveActionNodes");
        method.setAccessible(true);
        try {
            method.invoke(pipeline);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SubmissionException submissionException) {
                throw submissionException;
            }
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static void invokeCollectFormFieldInfo(FormSubmissionPipeline pipeline) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("collectFormFieldInfo");
        method.setAccessible(true);
        try {
            method.invoke(pipeline);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SubmissionException submissionException) {
                throw submissionException;
            }
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
