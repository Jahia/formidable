import {DIRECT_SUBMIT_PATH, EXPERIAN_STUB_PATH, FIELD_ACTION_PATH, ZEROBOUNCE_STUB_PATH} from '../../support/constants';
import {
	createPublishedLiveFormPage,
	getExperianEmailFieldActionNode,
	getInputEmailNode,
	getLatestLiveFormSubmission,
	getSaveToJcrActionNode,
	getZeroBounceEmailFieldActionNode,
	visitLiveForm,
	withFieldActions
} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

/** The samples module's double of Experian, declared as a development provider: plain HTTP on the instance itself. */
const EXPERIAN_STUB = `experian-stub|Experian (stub)|http://localhost:8080${EXPERIAN_STUB_PATH}|Auth-Token|stub-token`;
/** The double of ZeroBounce, whose key goes on the URL: the provider line's query placement. */
const ZEROBOUNCE_STUB = `zerobounce-stub|ZeroBounce (stub)|http://localhost:8080${ZEROBOUNCE_STUB_PATH}|api_key|stub-token|query`;
const STUB_PROVIDERS = [EXPERIAN_STUB, ZEROBOUNCE_STUB].join('\n');
/** The Experian double behind a token it does not accept: what a mistyped credential does. */
const WRONG_TOKEN_PROVIDERS = [`experian-stub|Experian (stub)|http://localhost:8080${EXPERIAN_STUB_PATH}|Auth-Token|another-token`, ZEROBOUNCE_STUB].join('\n');

const setDevelopmentProviders = (providers: string, enabled = true): Cypress.Chainable => cy.runProvisioningScript({
	script: {
		fileContent: JSON.stringify([{
			editConfiguration: 'org.jahia.modules.formidable',
			properties: {enableDevFieldActionProviders: String(enabled), devFieldActionProviders: providers}
		}]),
		type: 'application/json'
	}
});

/**
 * Field actions behind a provider, end to end: the samples' example implementations against Experian Email
 * Validation and ZeroBounce (docs/architecture/field-actions.md, "Email verification behind a provider"), driven
 * through the samples' own doubles of the providers — the same operation, credential and JSON, the verdict decided
 * by the address. The one thing exercising the FieldActionGateway for real: the provider lines of the configuration,
 * the credential injected as a header or on the URL, the path under the base URL and the reading of the answer.
 */
describe('Actions - 74 Field actions behind a provider: the Experian and ZeroBounce samples against their doubles', () => {
	useFormidableSite();

	before(() => {
		// A provider over plain HTTP is a development setting: the list behind its switch, as for the forward targets.
		setDevelopmentProviders(STUB_PROVIDERS);
	});

	after(() => {
		setDevelopmentProviders('', false);
	});

	it('posts the address to the provider and turns its confidence into the verdict, at blur and at submission', () => {
		createPublishedLiveFormPage('experian-live-form', 'Experian Live Form', [
			withFieldActions(getInputEmailNode({name: 'email', title: 'Email'}), [
				getExperianEmailFieldActionNode({name: 'mailbox', providerId: 'experian-stub', rejectionMessage: 'We cannot deliver to <b>${value}</b>.'})
			])
		], undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({livePath, formName}) => {
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`).as('check');
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submit');

			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();

			// The provider says undeliverable: refused under the field, the submission blocked in the browser.
			form.getEmailInput('email').get().type('ada@undeliverable.test').blur();
			cy.wait('@check').then(({request, response}) => {
				expect(request.body).to.deep.equal({field: 'email', value: 'ada@undeliverable.test', trigger: 'blur'});
				expect(response?.body.verdict).to.equal('reject');
			});
			cy.get('[data-fmdb-node-name="email"] .fmdb-validation-error').should('be.visible').and('contain.html', 'We cannot deliver to <b>ada@undeliverable.test</b>.');
			form.submit();
			cy.get('@submit.all').should('have.length', 0);

			// The provider says verified: the message goes; the field is asked once more before the request leaves, and it goes through.
			form.getEmailInput('email').get().clear().type('ada@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			cy.get('[data-fmdb-node-name="email"] .fmdb-validation-error').should('not.exist');
			form.submit();
			cy.wait('@check').its('request.body.trigger').should('equal', 'submit');
			cy.wait('@submit').its('response.statusCode').should('equal', 200);
			form.getSuccessMessage().should('be.visible');

			// The pipeline is the authority: the pre-check stubbed to accept, the same provider refuses at submission —
			// FMDB-015 anchored under the field, no global error, nothing stored.
			getLatestLiveFormSubmission(formName).then(({path: storedBefore}) => {
				cy.intercept('POST', `**${FIELD_ACTION_PATH}*`, {statusCode: 200, body: {verdict: 'accept', messages: []}}).as('stubbedCheck');
				const again = visitLiveForm(livePath);
				again.waitUntilHydrated();
				again.getEmailInput('email').get().type('bob@disposable.test');
				again.submit();
				cy.wait('@stubbedCheck');
				cy.wait('@submit').then(({response}) => {
					expect(response?.statusCode).to.equal(422);
					expect(response?.body.errorCode).to.equal('FMDB-015');
				});
				cy.get('[data-fmdb-node-name="email"] .fmdb-validation-error').should('be.visible').and('contain.html', '<b>bob@disposable.test</b>');
				cy.focused().should('have.attr', 'name', 'email');
				again.getErrorMessage().should('not.exist');
				getLatestLiveFormSubmission(formName).its('path').should('equal', storedBefore);
			});
		});
	});

	it('an answer the provider cannot conclude, or a token it refuses, is a check that could not run: the contributor decides', () => {
		createPublishedLiveFormPage('experian-unavailable-form', 'Experian Unavailable Form', [
			withFieldActions(getInputEmailNode({name: 'lenient', title: 'Lenient'}), [
				getExperianEmailFieldActionNode({name: 'mailbox', providerId: 'experian-stub'})
			]),
			withFieldActions(getInputEmailNode({name: 'strict', title: 'Strict'}), [
				getExperianEmailFieldActionNode({name: 'mailbox', providerId: 'experian-stub', whenUnavailable: 'reject', rejectionMessage: 'We could not check <b>${value}</b>.'})
			])
		], undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({livePath}) => {
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`).as('check');

			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();

			// "unknown": the lenient field accepts (the CND default), the strict one refuses with its message.
			form.getEmailInput('lenient').get().type('ada@unknown.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			cy.get('[data-fmdb-node-name="lenient"] .fmdb-validation-error').should('not.exist');
			form.getEmailInput('strict').get().type('ada@unknown.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'reject');
			cy.get('[data-fmdb-node-name="strict"] .fmdb-validation-error').should('be.visible').and('contain.html', 'We could not check <b>ada@unknown.test</b>.');

			// A token the provider refuses (401) is the same outage — and proves the header the gateway sent is the
			// configuration's: the same double, which verifies any ordinary address, now cannot be asked.
			setDevelopmentProviders(WRONG_TOKEN_PROVIDERS);
			form.getEmailInput('lenient').get().clear().type('bob@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			form.getEmailInput('strict').get().clear().type('bob@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'reject');
			cy.get('[data-fmdb-node-name="strict"] .fmdb-validation-error').should('be.visible').and('contain.html', '<b>bob@example.test</b>');
			setDevelopmentProviders(STUB_PROVIDERS);
		});
	});

	it('the ZeroBounce example, on the same engine base: the key on the URL, the status read for what a form wants to know', () => {
		createPublishedLiveFormPage('zerobounce-live-form', 'ZeroBounce Live Form', [
			withFieldActions(getInputEmailNode({name: 'email', title: 'Email'}), [
				getZeroBounceEmailFieldActionNode({name: 'mailbox', providerId: 'zerobounce-stub', rejectionMessage: 'Not a mailbox we can reach: <b>${value}</b>.'})
			])
		], undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({livePath}) => {
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`).as('check');
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submit');

			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();

			// invalid: refused with the message; a role address: accepted, it receives mail; catch-all: the contributor's default
			form.getEmailInput('email').get().type('ada@invalid.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'reject');
			cy.get('[data-fmdb-node-name="email"] .fmdb-validation-error').should('be.visible').and('contain.html', 'Not a mailbox we can reach: <b>ada@invalid.test</b>.');
			form.getEmailInput('email').get().clear().type('info@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			cy.get('[data-fmdb-node-name="email"] .fmdb-validation-error').should('not.exist');
			form.getEmailInput('email').get().clear().type('ada@catchall.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');

			// valid: through, asked once more before the request leaves
			form.getEmailInput('email').get().clear().type('ada@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			form.submit();
			cy.wait('@check').its('request.body.trigger').should('equal', 'submit');
			cy.wait('@submit').its('response.statusCode').should('equal', 200);
			form.getSuccessMessage().should('be.visible');
		});
	});
});
