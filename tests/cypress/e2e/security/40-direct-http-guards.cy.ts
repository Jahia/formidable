import {getInputTextNode} from '../../support/fixtures';
import {createPublishedLiveFormPage} from '../../support/fixtures';
import {
	expectCsrfRejectedResponse,
	expectErrorResponse,
	expectSuccessResponse,
	FOREIGN_ORIGIN,
	liveFormPath,
	postDirectMultipartSubmission,
	postDirectMultipartSubmissionViaPatchedXhr,
	postDirectNonMultipartSubmission,
	useFormidableSite,
	withSameOriginHeaders
} from './support';

describe('Security - direct HTTP submission guards', () => {
	useFormidableSite();

	let publicFormId: string;
	let authenticatedFormId: string;
	let authenticatedFormLivePath: string;
	let captchaFormId: string;
	let authenticatedCaptchaFormId: string;

	before(() => {
		cy.login();

		createPublishedLiveFormPage(
			'security-public-form',
			'Security public form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})]
		).then(({formId}) => {
			publicFormId = formId;
		});

		createPublishedLiveFormPage(
			'security-auth-form',
			'Security auth form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'security-auth-form-page',
			'Security auth form page',
			{mixins: ['fmdbmix:requireAuthentication']}
		).then(({formId, livePath}) => {
			authenticatedFormId = formId;
			authenticatedFormLivePath = livePath;
		});

		createPublishedLiveFormPage(
			'security-captcha-form',
			'Security captcha form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'security-captcha-form-page',
			'Security captcha form page',
			{mixins: ['fmdbmix:captcha']}
		).then(({formId}) => {
			captchaFormId = formId;
		});

		createPublishedLiveFormPage(
			'security-auth-captcha-form',
			'Security auth captcha form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'security-auth-captcha-form-page',
			'Security auth captcha form page',
			{mixins: ['fmdbmix:requireAuthentication', 'fmdbmix:captcha']}
		).then(({formId}) => {
			authenticatedCaptchaFormId = formId;
		});

		cy.logout();
	});

	it('rejects a direct multipart POST when no same-origin signal is present', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: publicFormId,
			fields: {fullName: 'Mallory'}
		}).then(response => {
			expectErrorResponse(response, 403, 'FMDB-011');
		});
	});

	it('rejects a direct multipart POST from a foreign origin', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: publicFormId,
			fields: {fullName: 'Mallory'},
			headers: {Origin: FOREIGN_ORIGIN}
		}).then(response => {
			expectErrorResponse(response, 403, 'FMDB-011');
		});
	});

	it('accepts a same-origin direct multipart POST on a public form', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: publicFormId,
			fields: {fullName: 'Alice'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectSuccessResponse(response);
		});
	});

	it('rejects non-multipart content before parsing the request body', () => {
		cy.logout();

		postDirectNonMultipartSubmission(publicFormId, withSameOriginHeaders())
			.then(response => {
				expectErrorResponse(response, 415, 'FMDB-001');
			});
	});

	it('rejects a multipart request when fid is missing', () => {
		cy.logout();

		postDirectMultipartSubmission({
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectErrorResponse(response, 400, 'FMDB-002');
		});
	});

	it('rejects a multipart request when fid is not a UUID', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: 'not-a-uuid',
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectErrorResponse(response, 400, 'FMDB-002');
		});
	});

	it('rejects a multipart request when the form UUID does not resolve in live', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: '11111111-1111-1111-1111-111111111111',
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectErrorResponse(response, 400, 'FMDB-004');
		});
	});

	it('rejects a guest direct submission on an authenticated form', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: authenticatedFormId,
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectErrorResponse(response, 401, 'FMDB-009');
		});
	});

	it('rejects an authenticated direct submission when no CSRF token is present', () => {
		postDirectMultipartSubmission({
			formId: authenticatedFormId,
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectCsrfRejectedResponse(response);
		});
	});

	it('accepts an authenticated direct submission when CSRFGuard injects a valid token', () => {
		cy.visit(liveFormPath({livePath: authenticatedFormLivePath}));

		postDirectMultipartSubmissionViaPatchedXhr({
			formId: authenticatedFormId,
			fields: {fullName: 'Alice'}
		}).then(response => {
			expect(response.status).to.eq(200);
			expect(response.body).to.deep.equal({success: true});
		});
	});

	it('rejects a CAPTCHA-protected form when server-side verification is not configured', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: captchaFormId,
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectErrorResponse(response, 500, 'FMDB-005');
		});
	});

	it('still rejects an unconfigured CAPTCHA-protected form even when a token header is sent', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: captchaFormId,
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders({
				'X-Formidable-Captcha-Token': 'fake-token'
			})
		}).then(response => {
			expectErrorResponse(response, 500, 'FMDB-005');
		});
	});

	it('rejects a guest on an auth-plus-captcha form before evaluating CAPTCHA', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: authenticatedCaptchaFormId,
			fields: {fullName: 'Mallory'},
			headers: withSameOriginHeaders({
				'X-Formidable-Captcha-Token': 'fake-token'
			})
		}).then(response => {
			expectErrorResponse(response, 401, 'FMDB-009');
		});
	});
});
