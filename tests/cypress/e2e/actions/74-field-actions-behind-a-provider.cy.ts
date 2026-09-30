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

/** Each sample's own configuration: where it calls, its credential, and that the URL is a double on this instance. */
const EXPERIAN_PID = 'org.jahia.test.modules.formidable.samples.experian';
const ZEROBOUNCE_PID = 'org.jahia.test.modules.formidable.samples.zerobounce';

/** Points a sample at its double, as the samples module ships it — or with a token the double does not accept. */
const configureSample = (pid: string, stubPath: string, credential = 'stub-token'): Cypress.Chainable => cy.runProvisioningScript({
	script: {
		fileContent: JSON.stringify([{
			editConfiguration: pid,
			properties: {url: `http://localhost:8080${stubPath}`, credential, development: 'true'}
		}]),
		type: 'application/json'
	}
});

/** The pre-check asked from the test itself, as the page asks it: same origin, one field, one value. */
const askDirectly = (formId: string, field: string, value: string): Cypress.Chainable<Cypress.Response<{verdict?: string}>> => {
	const origin = (Cypress.config('baseUrl') as string | null) ?? 'http://localhost:8080';
	return cy.request({
		method: 'POST',
		url: `${FIELD_ACTION_PATH}?fid=${formId}&lang=en`,
		headers: {'Content-Type': 'application/json', Origin: origin, Referer: `${origin}/`},
		body: {field, value, trigger: 'blur'},
		failOnStatusCode: false
	});
};

/**
 * Field actions behind a provider, end to end: the samples' example implementations against Experian Email
 * Validation and ZeroBounce (docs/architecture/field-actions.md, "Email verification behind a provider"), driven
 * through the samples' own doubles of the providers — the same operation, credential and JSON, the verdict decided
 * by the address. The one thing exercising the FieldActionGateway for real: each sample's own configuration read
 * into an endpoint, the credential injected as a header or on the URL, the path under the base URL and the reading of
 * the answer.
 */
describe('Actions - 74 Field actions behind a provider: the Experian and ZeroBounce samples against their doubles', () => {
	useFormidableSite();

	before(() => {
		// The samples ship these values; set them anyway, so that an instance whose files were edited runs the same.
		configureSample(EXPERIAN_PID, EXPERIAN_STUB_PATH);
		configureSample(ZEROBOUNCE_PID, ZEROBOUNCE_STUB_PATH);
	});

	after(() => {
		// Restored whatever happened: the file exists, so Jahia never copies the shipped one back.
		configureSample(EXPERIAN_PID, EXPERIAN_STUB_PATH);
	});

	it('posts the address to the provider and turns its confidence into the verdict, at blur and at submission', () => {
		createPublishedLiveFormPage('experian-live-form', 'Experian Live Form', [
			withFieldActions(getInputEmailNode({name: 'email', title: 'Email'}), [
				getExperianEmailFieldActionNode({name: 'mailbox', rejectionMessage: 'We cannot deliver to <b>${value}</b>.'})
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
			form.getSubmitButton().get().should('be.disabled');
			cy.get('@submit.all').should('have.length', 0);

			// The provider says verified: the message goes; the field is asked once more before the request leaves, and it goes through.
			form.getEmailInput('email').get().clear().type('ada@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			cy.get('[data-fmdb-node-name="email"] .fmdb-validation-error').should('not.exist');
			form.getSubmitButton().get().should('not.be.disabled');
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
				getExperianEmailFieldActionNode({name: 'mailbox'})
			]),
			withFieldActions(getInputEmailNode({name: 'strict', title: 'Strict'}), [
				getExperianEmailFieldActionNode({name: 'mailbox', whenUnavailable: 'reject', rejectionMessage: 'We could not check <b>${value}</b>.'})
			])
		], undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({livePath, formId}) => {
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
			configureSample(EXPERIAN_PID, EXPERIAN_STUB_PATH, 'another-token');
			// editConfiguration reaches the gateway asynchronously (spec 46 says why): poll the strict field with a fresh
			// address each time — an accept is cached per value — until the refused token is what answers.
			cy.waitUntil(() => askDirectly(formId, 'strict', `poll-${Date.now()}@example.test`)
				.then(response => response.body?.verdict === 'reject'),
			{timeout: 15000, interval: 1000, errorMsg: 'the wrong token never reached the gateway'});
			form.getEmailInput('lenient').get().clear().type('bob@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			form.getEmailInput('strict').get().clear().type('bob@example.test').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'reject');
			cy.get('[data-fmdb-node-name="strict"] .fmdb-validation-error').should('be.visible').and('contain.html', '<b>bob@example.test</b>');
		});
	});

	it('the ZeroBounce example, on the same engine base: the key on the URL, the status read for what a form wants to know', () => {
		createPublishedLiveFormPage('zerobounce-live-form', 'ZeroBounce Live Form', [
			withFieldActions(getInputEmailNode({name: 'email', title: 'Email'}), [
				getZeroBounceEmailFieldActionNode({name: 'mailbox', rejectionMessage: 'Not a mailbox we can reach: <b>${value}</b>.'})
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
