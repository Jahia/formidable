package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUser;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormSubmissionPipelineTest {

    @Test
    void verifyMultipartRejectsNonMultipartRequest() {
        // Verifies the content-type gate: non-multipart requests must be rejected
        // before any routing, JCR lookup, or parsing is attempted.
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("application/json");

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyMultipart(new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of()), req));

        // Expected outcome: FMDB-001 is returned for non-multipart submissions.
        assertEquals(ErrorCode.FMDB_001, error.errorCode);
    }

    @Test
    void readRoutingParamsRejectsWhenFidIsMissing() {
        // Verifies the routing gate: submissions without the mandatory form UUID
        // must be rejected before the pipeline can resolve any form node.
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("fid")).thenReturn(null);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeReadRoutingParams(new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of()), req));

        // Expected outcome: FMDB-002 is returned for a missing fid.
        assertEquals(ErrorCode.FMDB_002, error.errorCode);
    }

    @Test
    void readRoutingParamsRejectsWhenFidIsNotAUuid() {
        // Verifies UUID validation on the routing parameter.
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("fid")).thenReturn("not-a-uuid");

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeReadRoutingParams(new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of()), req));

        // Expected outcome: FMDB-002 is returned for a malformed fid.
        assertEquals(ErrorCode.FMDB_002, error.errorCode);
    }

    @Test
    void readRoutingParamsDefaultsLocaleToEnglishWhenLangIsMissing() throws Exception {
        // Verifies locale fallback: if the caller omits lang,
        // the pipeline must keep the documented English default.
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of());
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("fid")).thenReturn(UUID.randomUUID().toString());
        when(req.getParameter("lang")).thenReturn(null);

        invokeReadRoutingParams(pipeline, req);

        // Expected outcome: the pipeline stores Locale.ENGLISH.
        assertEquals(Locale.ENGLISH, getField(pipeline, "locale"));
    }

    @Test
    void guardContentLengthRejectsOversizedRequest() {
        // Verifies the early size gate: when Content-Length is present and exceeds
        // the configured limit, the servlet should fail before body parsing starts.
        FormidableConfigService config = mock(FormidableConfigService.class);
        when(config.getUploadMaxRequestSizeBytes()).thenReturn(10L);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(config, List.<FormAction>of());
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentLengthLong()).thenReturn(11L);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeGuardContentLength(pipeline, req));

        // Expected outcome: FMDB-003 is returned for oversized requests.
        assertEquals(ErrorCode.FMDB_003, error.errorCode);
    }

    @Test
    void guardContentLengthAllowsChunkedRequestWithoutEarlyRejection() {
        // Verifies the chunked-request path: the early guard must not reject
        // when Content-Length is unavailable and returns -1.
        FormidableConfigService config = mock(FormidableConfigService.class);
        when(config.getUploadMaxRequestSizeBytes()).thenReturn(10L);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(config, List.<FormAction>of());
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentLengthLong()).thenReturn(-1L);

        // Expected outcome: the pipeline defers definitive size enforcement to multipart parsing.
        assertDoesNotThrow(() -> invokeGuardContentLength(pipeline, req));
    }

    @Test
    void resolveFormNodeRejectsWhenFormCannotBeResolvedInLiveWorkspace() throws Exception {
        // Verifies the live-workspace lookup gate: if the target form UUID cannot
        // be resolved, the submission must be rejected as an invalid target form.
        org.jahia.services.content.JCRSessionWrapper session = mock(org.jahia.services.content.JCRSessionWrapper.class);
        when(session.getNodeByIdentifier("test-form-id")).thenThrow(new RepositoryException("missing"));

        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                mock(FormidableConfigService.class),
                List.<FormAction>of(),
                FormFieldMetadataCollector::collect,
                JCRTemplate::getInstance,
                FormDataParser::parseAll,
                locale -> session
        );
        setField(pipeline, "formId", "test-form-id");
        setField(pipeline, "locale", Locale.ENGLISH);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeResolveFormNode(pipeline));

        // Expected outcome: FMDB-004 is returned when the live form node is unavailable.
        assertEquals(ErrorCode.FMDB_004, error.errorCode);
    }

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
    void verifyCaptchaUsesEngineOwnedSemanticMixin() throws Exception {
        // Verifies the ownership split: the pipeline must read fmdbmix:captchaProtectedForm
        // instead of the elements-owned wrapper mixin applied by authors.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(config, List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(formNode.isNodeType("fmdbmix:captchaProtectedForm")).thenReturn(false);
        setField(pipeline, "formNode", formNode);
        setField(pipeline, "formId", "test-form-id");

        assertDoesNotThrow(() -> invokeVerifyCaptcha(pipeline, req));

        // Expected outcome: fmdbmix:captchaProtectedForm is consulted directly by the pipeline.
        verify(formNode).isNodeType("fmdbmix:captchaProtectedForm");
    }

    @Test
    void verifyCaptchaSkipsValidationWhenFormDoesNotRequireCaptcha() throws Exception {
        // Verifies the bypass case: if the mixin is absent, the CAPTCHA gate must not inspect config or token.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = newPipelineWithCaptchaFormNode(config, false);
        HttpServletRequest req = mock(HttpServletRequest.class);

        // Expected outcome: no exception is raised because CAPTCHA is not enabled on the form.
        assertDoesNotThrow(() -> invokeVerifyCaptcha(pipeline, req));
    }

    @Test
    void verifyCaptchaRejectsWhenRequiredButServerIsNotConfigured() throws Exception {
        // Verifies the configuration gate: a CAPTCHA-protected form must be blocked when the server is not configured.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = newPipelineWithCaptchaFormNode(config, true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(config.isCaptchaVerificationConfigured()).thenReturn(false);
        when(((JCRNodeWrapper) getField(pipeline, "formNode")).getPath()).thenReturn("/sites/test/form");

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyCaptcha(pipeline, req));

        // Expected outcome: FMDB-005 is returned because the form requires CAPTCHA but the server cannot verify it.
        assertEquals(ErrorCode.FMDB_005, error.errorCode);
    }

    @Test
    void verifyCaptchaRejectsWhenTokenIsInvalid() throws Exception {
        // Verifies the provider gate: an invalid or missing token must reject the submission on CAPTCHA-protected forms.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = newPipelineWithCaptchaFormNode(config, true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(config.isCaptchaVerificationConfigured()).thenReturn(true);
        when(req.getHeader("X-Formidable-Captcha-Token")).thenReturn("bad-token");
        when(req.getRemoteAddr()).thenReturn("203.0.113.10");
        when(config.verifyCaptcha("bad-token", "203.0.113.10")).thenReturn(false);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyCaptcha(pipeline, req));

        // Expected outcome: FMDB-006 is returned because the provider rejected the submitted token.
        assertEquals(ErrorCode.FMDB_006, error.errorCode);
    }

    @Test
    void verifyCaptchaRejectsWhenTokenHeaderIsMissing() throws Exception {
        // Verifies the missing-token path with the current transport contract:
        // the provider token must be carried by the dedicated request header.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = newPipelineWithCaptchaFormNode(config, true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(config.isCaptchaVerificationConfigured()).thenReturn(true);
        when(req.getHeader("X-Formidable-Captcha-Token")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("203.0.113.10");
        when(config.verifyCaptcha(null, "203.0.113.10")).thenReturn(false);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyCaptcha(pipeline, req));

        // Expected outcome: FMDB-006 is returned when the CAPTCHA header is absent.
        assertEquals(ErrorCode.FMDB_006, error.errorCode);
    }

    @Test
    void verifyCaptchaAllowsSubmissionWhenTokenIsValid() throws Exception {
        // Verifies the positive path: a valid token on a CAPTCHA-protected form must pass the gate.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = newPipelineWithCaptchaFormNode(config, true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(config.isCaptchaVerificationConfigured()).thenReturn(true);
        when(req.getHeader("X-Formidable-Captcha-Token")).thenReturn("valid-token");
        when(req.getRemoteAddr()).thenReturn("203.0.113.10");
        when(config.verifyCaptcha("valid-token", "203.0.113.10")).thenReturn(true);

        // Expected outcome: no exception is raised and the pipeline can continue.
        assertDoesNotThrow(() -> invokeVerifyCaptcha(pipeline, req));
    }

    @Test
    void verifyCaptchaFailsWithInternalErrorWhenVerificationIsTechnicallyUnavailable() throws Exception {
        // Verifies the provider-failure path: technical verification errors must surface
        // as internal failures rather than validation failures.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = newPipelineWithCaptchaFormNode(config, true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(config.isCaptchaVerificationConfigured()).thenReturn(true);
        when(req.getHeader("X-Formidable-Captcha-Token")).thenReturn("valid-token");
        when(req.getRemoteAddr()).thenReturn("203.0.113.10");
        when(config.verifyCaptcha("valid-token", "203.0.113.10"))
                .thenThrow(new FormidableConfigService.CaptchaVerificationException("provider unavailable", new RuntimeException("timeout")));

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyCaptcha(pipeline, req));

        // Expected outcome: FMDB-500 is returned for technical provider failures.
        assertEquals(ErrorCode.FMDB_500, error.errorCode);
    }

    @Test
    void verifyCaptchaFailsClosedWhenMixinLookupThrows() throws Exception {
        // Verifies the fail-closed path: a repository error during mixin lookup must reject the submission.
        FormidableConfigService config = mock(FormidableConfigService.class);
        FormSubmissionPipeline pipeline = newPipelineWithBrokenCaptchaFormNode(config);
        HttpServletRequest req = mock(HttpServletRequest.class);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeVerifyCaptcha(pipeline, req));

        // Expected outcome: FMDB-500 is returned because the CAPTCHA requirement cannot be verified.
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
                () -> template,
                FormDataParser::parseAll,
                locale -> mock(org.jahia.services.content.JCRSessionWrapper.class)
        );
        setField(pipeline, "formId", "test-form-id");

        when(template.doExecuteWithSystemSessionAsUser(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("live"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RepositoryException("boom"));

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeResolveActionNodes(pipeline));

        // Expected outcome: FMDB-012 is returned so action-list resolution failures stay
        // distinguishable from downstream action execution failures.
        assertEquals(ErrorCode.FMDB_012, error.errorCode);
    }

    @Test
    void collectFormFieldInfoRejectsSubmissionWhenMetadataCollectionFails() throws Exception {
        // Verifies the metadata gate: if JCR field metadata cannot be collected reliably,
        // the submission must fail instead of parsing against partial metadata.
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                mock(FormidableConfigService.class),
                List.<FormAction>of(),
                (formId, locale) -> { throw new RepositoryException("boom"); },
                JCRTemplate::getInstance,
                FormDataParser::parseAll,
                locale -> mock(org.jahia.services.content.JCRSessionWrapper.class)
        );
        setField(pipeline, "formId", "test-form-id");
        setField(pipeline, "locale", java.util.Locale.ENGLISH);

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeCollectFormFieldInfo(pipeline));

        // Expected outcome: FMDB-500 is returned because the submission cannot rely on partial metadata.
        assertEquals(ErrorCode.FMDB_500, error.errorCode);
    }

    @Test
    void parseMultipartMapsValidationFailuresToFMDB010() throws Exception {
        // Verifies the parser error mapping for user-data validation failures.
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                mock(FormidableConfigService.class),
                List.<FormAction>of(),
                FormFieldMetadataCollector::collect,
                JCRTemplate::getInstance,
                (req, config, metadata) -> {
                    throw new FormDataParser.ParseException("bad data", FormDataParser.ParseException.FailureType.VALIDATION);
                },
                locale -> mock(org.jahia.services.content.JCRSessionWrapper.class)
        );
        setField(pipeline, "fieldMetadata", emptyFieldMetadata());

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeParseMultipart(pipeline, mock(HttpServletRequest.class)));

        // Expected outcome: parser validation failures map to FMDB-010.
        assertEquals(ErrorCode.FMDB_010, error.errorCode);
    }

    @Test
    void parseMultipartMapsTechnicalFailuresToFMDB007() throws Exception {
        // Verifies the parser error mapping for low-level multipart or stream failures.
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                mock(FormidableConfigService.class),
                List.<FormAction>of(),
                FormFieldMetadataCollector::collect,
                JCRTemplate::getInstance,
                (req, config, metadata) -> {
                    throw new FormDataParser.ParseException("stream failed", FormDataParser.ParseException.FailureType.TECHNICAL);
                },
                locale -> mock(org.jahia.services.content.JCRSessionWrapper.class)
        );
        setField(pipeline, "fieldMetadata", emptyFieldMetadata());

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeParseMultipart(pipeline, mock(HttpServletRequest.class)));

        // Expected outcome: parser technical failures map to FMDB-007.
        assertEquals(ErrorCode.FMDB_007, error.errorCode);
    }

    @Test
    void parseMultipartMapsConfigurationFailuresToFMDB500() throws Exception {
        // Verifies the parser error mapping for invalid server-side metadata or configuration.
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                mock(FormidableConfigService.class),
                List.<FormAction>of(),
                FormFieldMetadataCollector::collect,
                JCRTemplate::getInstance,
                (req, config, metadata) -> {
                    throw new FormDataParser.ParseException("bad config", FormDataParser.ParseException.FailureType.CONFIGURATION);
                },
                locale -> mock(org.jahia.services.content.JCRSessionWrapper.class)
        );
        setField(pipeline, "fieldMetadata", emptyFieldMetadata());

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeParseMultipart(pipeline, mock(HttpServletRequest.class)));

        // Expected outcome: parser configuration failures map to FMDB-500.
        assertEquals(ErrorCode.FMDB_500, error.errorCode);
    }

    @Test
    void runRejectsGuestBeforeEvaluatingCaptchaWhenFormRequiresBothGuards() {
        // Verifies gate ordering: authenticated-only forms must reject Guest users
        // before the CAPTCHA requirement is even evaluated.
        // Expected outcome: the pipeline returns FMDB-009 and never checks the CAPTCHA mixin.
        FormidableConfigService config = mock(FormidableConfigService.class);
        org.jahia.services.content.JCRSessionWrapper session = mock(org.jahia.services.content.JCRSessionWrapper.class);
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        String formId = UUID.randomUUID().toString();
        HttpServletRequest req = mock(HttpServletRequest.class);
        JahiaUser guestUser = mock(JahiaUser.class);

        when(session.getNodeByIdentifier(formId)).thenReturn(formNode);
        when(formNode.isNodeType("fmdbmix:authenticatedOnlyForm")).thenReturn(true);
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("multipart/form-data; boundary=test");
        when(req.getParameter("fid")).thenReturn(formId);
        when(req.getParameter("lang")).thenReturn("en");
        when(req.getContentLengthLong()).thenReturn(-1L);
        when(guestUser.getName()).thenReturn("guest");

        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                config,
                List.<FormAction>of(),
                (ignoredFormId, ignoredLocale) -> emptyFieldMetadata(),
                JCRTemplate::getInstance,
                (request, cfg, metadata) -> new FormDataParser.ParseResult(java.util.Map.of(), List.of()),
                locale -> session
        );

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeRun(pipeline, req, guestUser));

        assertEquals(ErrorCode.FMDB_009, error.errorCode);
        verify(formNode).isNodeType("fmdbmix:authenticatedOnlyForm");
        verify(formNode, never()).isNodeType("fmdbmix:captchaProtectedForm");
    }

    @Test
    void runRejectsAuthenticatedUserWithoutCaptchaTokenWhenFormRequiresBothGuards() {
        // Verifies combined gate ordering: once authentication passes for a logged-in user,
        // the CAPTCHA gate must still reject submissions that lack the token header.
        FormidableConfigService config = mock(FormidableConfigService.class);
        org.jahia.services.content.JCRSessionWrapper session = mock(org.jahia.services.content.JCRSessionWrapper.class);
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        String formId = UUID.randomUUID().toString();
        HttpServletRequest req = mock(HttpServletRequest.class);
        JahiaUser authenticatedUser = mock(JahiaUser.class);

        when(session.getNodeByIdentifier(formId)).thenReturn(formNode);
        when(formNode.isNodeType("fmdbmix:authenticatedOnlyForm")).thenReturn(true);
        when(formNode.isNodeType("fmdbmix:captchaProtectedForm")).thenReturn(true);
        when(formNode.getPath()).thenReturn("/sites/test/form");
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("multipart/form-data; boundary=test");
        when(req.getParameter("fid")).thenReturn(formId);
        when(req.getParameter("lang")).thenReturn("en");
        when(req.getContentLengthLong()).thenReturn(-1L);
        when(authenticatedUser.getName()).thenReturn("editor");
        when(config.isCaptchaVerificationConfigured()).thenReturn(true);
        when(req.getHeader("X-Formidable-Captcha-Token")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("203.0.113.10");
        when(config.verifyCaptcha(null, "203.0.113.10")).thenReturn(false);

        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                config,
                List.<FormAction>of(),
                (ignoredFormId, ignoredLocale) -> emptyFieldMetadata(),
                JCRTemplate::getInstance,
                (request, cfg, metadata) -> new FormDataParser.ParseResult(java.util.Map.of(), List.of()),
                locale -> session
        );

        SubmissionException error = assertThrows(SubmissionException.class,
                () -> invokeRun(pipeline, req, authenticatedUser));

        // Expected outcome: authentication passes but the missing CAPTCHA token triggers FMDB-006.
        assertEquals(ErrorCode.FMDB_006, error.errorCode);
        verify(formNode).isNodeType("fmdbmix:authenticatedOnlyForm");
        verify(formNode).isNodeType("fmdbmix:captchaProtectedForm");
    }

    private static FormSubmissionPipeline newPipelineWithFormNode(boolean requiresAuthentication) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:authenticatedOnlyForm")).thenReturn(requiresAuthentication);
        setField(pipeline, "formNode", formNode);
        setField(pipeline, "formId", "test-form-id");
        return pipeline;
    }

    private static FormSubmissionPipeline newPipelineWithCaptchaFormNode(FormidableConfigService config,
                                                                         boolean requiresCaptcha) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(config, List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:captchaProtectedForm")).thenReturn(requiresCaptcha);
        setField(pipeline, "formNode", formNode);
        setField(pipeline, "formId", "test-form-id");
        return pipeline;
    }

    private static FormSubmissionPipeline newPipelineWithBrokenCaptchaFormNode(FormidableConfigService config) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(config, List.<FormAction>of());
        JCRNodeWrapper formNode = mock(JCRNodeWrapper.class);
        when(formNode.isNodeType("fmdbmix:captchaProtectedForm")).thenThrow(new RepositoryException("boom"));
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

    private static void invokeVerifyMultipart(FormSubmissionPipeline pipeline, HttpServletRequest req) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("verifyMultipart", HttpServletRequest.class);
        method.setAccessible(true);
        invokeSubmissionStep(method, pipeline, req);
    }

    private static void invokeReadRoutingParams(FormSubmissionPipeline pipeline, HttpServletRequest req) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("readRoutingParams", HttpServletRequest.class);
        method.setAccessible(true);
        invokeSubmissionStep(method, pipeline, req);
    }

    private static void invokeGuardContentLength(FormSubmissionPipeline pipeline, HttpServletRequest req) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("guardContentLength", HttpServletRequest.class);
        method.setAccessible(true);
        invokeSubmissionStep(method, pipeline, req);
    }

    private static void invokeResolveFormNode(FormSubmissionPipeline pipeline) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("resolveFormNode");
        method.setAccessible(true);
        invokeSubmissionStep(method, pipeline);
    }

    private static void invokeVerifyCaptcha(FormSubmissionPipeline pipeline, HttpServletRequest req) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("verifyCaptcha", HttpServletRequest.class);
        method.setAccessible(true);
        invokeSubmissionStep(method, pipeline, req);
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
        invokeSubmissionStep(method, pipeline);
    }

    private static void invokeParseMultipart(FormSubmissionPipeline pipeline, HttpServletRequest req) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("parseMultipart", HttpServletRequest.class);
        method.setAccessible(true);
        invokeSubmissionStep(method, pipeline, req);
    }

    private static void invokeRun(FormSubmissionPipeline pipeline,
                                  HttpServletRequest req,
                                  JahiaUser currentUser) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("run", HttpServletRequest.class);
        method.setAccessible(true);
        JahiaUser previousUser = JCRSessionFactory.getInstance().getCurrentUser();
        try {
            JCRSessionFactory.getInstance().setCurrentUser(currentUser);
            invokeSubmissionStep(method, pipeline, req);
        } finally {
            JCRSessionFactory.getInstance().setCurrentUser(previousUser);
        }
    }

    private static FormFieldMetadataCollector.Result emptyFieldMetadata() {
        return new FormFieldMetadataCollector.Result(java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
    }

    private static void invokeSubmissionStep(Method method, Object target, Object... args) throws Exception {
        try {
            method.invoke(target, args);
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

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
