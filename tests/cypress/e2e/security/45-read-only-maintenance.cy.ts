import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import type {JahiaNode} from '../../support/fixtures';
import {getInputTextNode} from '../../support/fixtures';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures';
import {createPublishedLiveFormPage} from '../../support/fixtures/forms';
import {FORMIDABLE_MODULE_IDS} from '../../support/constants';
import {
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	withSameOriginHeaders
} from './support';

const SAVE_TO_JCR_ACTION: JahiaNode = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
};

// fmdb:emailNotificationAction carries fmdbmix:readOnlyCompatibleAction in the engine CND:
// a form using only such actions must keep submitting during maintenance. The pipeline gate
// runs before dispatch, so the (expectedly failing, no SMTP) send never matters for FMDB-014.
const EMAIL_ACTION: JahiaNode = {
	name: 'notifyByEmail',
	primaryNodeType: 'fmdb:emailNotificationAction',
	properties: [
		{name: 'to', value: 'ops@example.org'},
		{name: 'from', value: 'noreply@example.org'}
	]
};

// The groovy script throws when the controller does not reach the target status, and
// cy.executeGroovy surfaces that as a result containing ".failed" (the script return
// value itself is not propagated by the provisioning API).
const setReadOnlyMode = (enable: boolean) => {
	cy.executeGroovy('groovy/switchReadOnlyMode.groovy', {'__ENABLE__': String(enable)})
		.then(result => {
			expect(String(result), `read-only switch to ${enable ? 'ON' : 'OFF'}`).to.not.contain('.failed');
		});
};

describe('Security - read-only maintenance mode', () => {
	let savingFormId: string;
	let savingFormLivePath: string;
	let emailOnlyFormId: string;
	let customMessageFormLivePath: string;

	// No per-test login/logout on purpose: fixtures are created up front while the
	// platform is writable, then every request runs as Guest — logging in during the
	// read-only window would itself touch the repository.
	before(() => {
		// Defensive: fixture creation writes to the JCR, so never start read-only
		// (e.g. after an interrupted previous run).
		setReadOnlyMode(false);
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
		cy.login();

		createPublishedLiveFormPage(
			'readonly-saving-form',
			'Read-only saving form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'readonly-saving-form-page',
			'Read-only saving form page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({formId, livePath}) => {
			savingFormId = formId;
			savingFormLivePath = livePath;
		});

		createPublishedLiveFormPage(
			'readonly-email-form',
			'Read-only email form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'readonly-email-form-page',
			'Read-only email form page',
			{actions: [EMAIL_ACTION]}
		).then(({formId}) => {
			emailOnlyFormId = formId;
		});

		// Contributor-authored maintenance message, overriding the bundle default.
		createPublishedLiveFormPage(
			'readonly-custom-message-form',
			'Read-only custom message form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			'readonly-custom-message-form-page',
			'Read-only custom message form page',
			{
				actions: [SAVE_TO_JCR_ACTION],
				properties: [{name: 'maintenanceMessage', value: '<p>Back on Monday, promised.</p>', language: 'en'}]
			}
		).then(({livePath}) => {
			customMessageFormLivePath = livePath;
		});

		cy.logout();
	});

	// Defensive: never leave the platform read-only for the rest of the suite,
	// even when an assertion fails mid-spec.
	after(() => {
		setReadOnlyMode(false);
	});

	it('accepts submissions of a repository-writing form while the platform is writable', () => {
		postDirectMultipartSubmission({
			formId: savingFormId,
			fields: {fullName: 'Alice Baseline'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectSuccessResponse(response);
		});
	});

	it('rejects repository-writing forms with FMDB-014 and keeps read-only-compatible forms working while read-only', () => {
		setReadOnlyMode(true);

		// The saving form is blocked before any byte of the body is processed
		// and before any action side effect could run.
		postDirectMultipartSubmission({
			formId: savingFormId,
			fields: {fullName: 'Bob Maintenance'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectErrorResponse(response, 503, 'FMDB-014');
		});

		// The email-only form passes the maintenance gate: its single action declares
		// read-only compatibility. The dispatch itself then fails on the missing SMTP
		// configuration (FMDB-008), which proves the gate let the submission through.
		postDirectMultipartSubmission({
			formId: emailOnlyFormId,
			fields: {fullName: 'Carol Maintenance'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expect(response.status).to.not.eq(503);
			expect((response.body as {errorCode?: string}).errorCode).to.not.eq('FMDB-014');
		});
	});

	it('renders the maintenance state instead of an active form while read-only', () => {
		// The site is created by this spec, so this first live visit renders the fragment
		// fresh, after the switch — no stale cached fragment can hide the banner.
		cy.visit(`/en/sites/${FORMIDABLE_TEST_SITE.key}/${savingFormLivePath}`);

		// The banner text is the contributor property, autocreated from the
		// resource-bundle default when nothing is authored.
		cy.get('.fmdb-message-maintenance').should('be.visible')
			.and('contain.text', 'temporarily unavailable');
		cy.get('form.fmdb-form button[type="submit"]').should('be.disabled');

		// A contributor-authored message replaces the default.
		cy.visit(`/en/sites/${FORMIDABLE_TEST_SITE.key}/${customMessageFormLivePath}`);
		cy.get('.fmdb-message-maintenance').should('be.visible')
			.and('contain.text', 'Back on Monday, promised.');
	});

	it('accepts submissions again once read-only mode is switched off', () => {
		setReadOnlyMode(false);

		postDirectMultipartSubmission({
			formId: savingFormId,
			fields: {fullName: 'Dave Recovery'},
			headers: withSameOriginHeaders()
		}).then(response => {
			expectSuccessResponse(response);
		});
	});
});
