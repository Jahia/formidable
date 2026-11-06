import {DIRECT_SUBMIT_PATH, FIELD_ACTION_PATH} from '../../support/constants';
import {
	createPublishedLiveFormPage,
	getBlockedWordsFieldActionNode,
	getInputTextNode,
	getLatestLiveSubmissionValues,
	getPasswordNode,
	getPasswordPolicyFieldActionNode,
	getSaveToJcrActionNode,
	visitLiveForm,
	withFieldActions
} from '../../support/fixtures';
import {postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

/** The pre-check asked from the test itself, as the page asks it: same origin, one field, one value. */
const askDirectly = (formId: string, field: string, value: string): Cypress.Chainable<Cypress.Response<{verdict?: string; errorCode?: string}>> => {
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
 * Field actions on a password (docs/architecture/field-actions.md, "On a sensitive field"). A field action sends the
 * value it judges somewhere, so on a sensitive field the engine runs only the field actions whose Java type opts in
 * (FieldAction#consumesSensitiveValues), whatever the field's action list holds: at the pre-check endpoint, at
 * submission, and in the page, which asks the engine only about those. A generic check, the samples' blocked words,
 * never runs on the password: the page does not ask, the endpoint has nothing to run, the submission is accepted with
 * a blocked word in it. The samples' password policy opts in: the page asks it as the visitor leaves the field, the
 * pipeline refuses a password without a digit with FMDB-015 and accepts one with a digit. The contributor's message
 * never prints the password: `${value}` reads as nothing on a sensitive field.
 */
describe('Actions - 86 Field actions on a password', () => {
	useFormidableSite();

	it('never runs a field action that does not opt in, whatever the field holds', () => {
		const formName = 'password-generic-check-form';
		createPublishedLiveFormPage(formName, 'Password with a generic check', [
			getInputTextNode({name: 'username', title: 'Username'}),
			withFieldActions(getPasswordNode({name: 'password', title: 'Password'}), [
				getBlockedWordsFieldActionNode({name: 'noSecret', words: ['secret'], rejectionMessage: '<b>${value}</b> is not welcome.'})
			])
		], undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({formId, livePath}) => {
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`).as('check');
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submit');

			// The page: no marker on the password's wrapper, so leaving the field asks nothing, nor does the submission.
			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();
			cy.get('[data-fmdb-node-name="password"]').should('not.have.attr', 'data-fmdb-field-action');
			form.getTextInput('username').get().type('ada');
			cy.get('[data-fmdb-node-name="password"] input[type="password"]').type('my-secret-7');
			cy.get('[data-fmdb-node-name="password"] input[type="password"]').blur();
			form.submit();
			cy.wait('@submit').its('response.statusCode').should('equal', 200);
			cy.get('@check.all').should('have.length', 0);
			form.getSuccessMessage().should('be.visible');

			// The endpoint: the field holds no action the engine would run, so there is nothing to pre-check.
			askDirectly(formId, 'password', 'my-secret-7').then(response => {
				expect(response.status).to.equal(404);
				expect(response.body.errorCode).to.equal('FMDB-004');
			});

			// The pipeline: a blocked word in the password, posted directly, is accepted.
			cy.logout();
			postDirectMultipartSubmission({formId, fields: {username: 'grace', password: 'another-secret-8'}, headers: withSameOriginHeaders()})
				.then(response => {
					expect(response.status).to.eq(200);
					expect(response.body).to.deep.equal({success: true});
				});
		});

		cy.login();
		getLatestLiveSubmissionValues(formName).then(stored => {
			expect(stored.username).to.eq('grace');
			expect(Object.keys(stored)).to.not.include('password');
		});
	});

	it('runs the field action that opts in: a weak password is refused with FMDB-015, a strong one accepted', () => {
		const WEAK = 'no-digits-here';
		const STRONG = 'with-a-digit-9';
		const formName = 'password-policy-form';
		createPublishedLiveFormPage(formName, 'Password with a policy', [
			getInputTextNode({name: 'username', title: 'Username'}),
			withFieldActions(getPasswordNode({name: 'password', title: 'Password'}), [
				// ${value} is advertised by the editor; on a sensitive field it reads as nothing.
				getPasswordPolicyFieldActionNode({rejectionMessage: 'Your password <b>${value}</b> needs a digit.'}),
				// Next to it, a generic check that would refuse both passwords if it ran.
				getBlockedWordsFieldActionNode({name: 'noDigit', words: ['digit', 'here']})
			])
		], undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({formId, livePath}) => {
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`).as('check');
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submit');

			// The page asks about the password, for the policy alone, as the visitor leaves it.
			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();
			cy.get('[data-fmdb-node-name="password"]').should('have.attr', 'data-fmdb-field-action', 'blur');
			cy.get('[data-fmdb-node-name="password"] input[type="password"]').type(WEAK);
			cy.get('[data-fmdb-node-name="password"] input[type="password"]').blur();
			cy.wait('@check').then(({request, response}) => {
				expect(request.body).to.deep.equal({field: 'password', value: WEAK, trigger: 'blur'});
				expect(response?.body.verdict).to.equal('reject');
				expect(JSON.stringify(response?.body)).to.not.contain(WEAK);
			});
			cy.get('[data-fmdb-node-name="password"] .fmdb-validation-error')
				.should('be.visible')
				.and('contain.text', 'needs a digit')
				.and('not.contain.text', WEAK);

			// The pipeline is the authority: posted directly, the weak password is refused, the message anchored on the
			// field, and the body holds no password.
			cy.logout();
			postDirectMultipartSubmission({formId, fields: {username: 'ada', password: WEAK}, headers: withSameOriginHeaders()})
				.then(response => {
					const body = response.body as {success: boolean; errorCode: string; messages: Array<{field: string; html: string}>};
					expect(response.status).to.eq(422);
					expect(body.errorCode).to.eq('FMDB-015');
					expect(body.messages.map(message => message.field)).to.deep.equal(['password']);
					expect(body.messages[0].html).to.contain('needs a digit');
					expect(JSON.stringify(body)).to.not.contain(WEAK);
				});

			// A password with a digit passes the policy; the blocked words never ran, or "digit" would refuse it.
			postDirectMultipartSubmission({formId, fields: {username: 'ada', password: STRONG}, headers: withSameOriginHeaders()})
				.then(response => {
					expect(response.status).to.eq(200);
					expect(response.body).to.deep.equal({success: true});
				});
		});

		cy.login();
		getLatestLiveSubmissionValues(formName).then(stored => {
			expect(stored.username).to.eq('ada');
			expect(Object.keys(stored)).to.not.include('password');
			expect(JSON.stringify(stored)).to.not.contain(STRONG);
		});
	});
});
