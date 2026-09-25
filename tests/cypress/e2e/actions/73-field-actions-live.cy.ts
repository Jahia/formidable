import {enableModule} from '@jahia/cypress';
import {DIRECT_SUBMIT_PATH, FIELD_ACTION_PATH} from '../../support/constants';
import {
	createPublishedLiveFormPage,
	FORMIDABLE_TEST_SITE,
	getBlockedWordsFieldActionNode,
	getInputEmailNode,
	getInputTextNode,
	getLatestLiveFormSubmission,
	getMinimumWordsFieldActionNode,
	getSaveToJcrActionNode,
	getTextareaNode,
	visitLiveForm,
	withFieldActions
} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

/** The sample module whose field action is written in JavaScript — a hidden.execute view the engine renders. */
const SAMPLE_MODULE = 'formidable-test-module-samples-tsx';

const setRateLimit = (perMinute: string): Cypress.Chainable => cy.runProvisioningScript({
	script: {
		fileContent: JSON.stringify([{editConfiguration: 'org.jahia.modules.formidable', properties: {fieldActionRateLimitPerMinute: perMinute}}]),
		type: 'application/json'
	}
});

/**
 * The visitor side of the field actions (docs/architecture/field-actions.md, "The browser"). As the
 * visitor leaves a field checked at blur, the page asks the engine about the value and shows the
 * contributor's message under the field: a refusal blocks the submission until the value changes, a
 * warning advises and lets it through, a corrected value clears the message. Before the submission
 * is sent, every field with actions is asked again — the submit-only ones for the first time — and a
 * refusal stops the request. The pre-check is a courtesy and the pipeline the authority: a browser
 * that skips it (the check stubbed, or refused by the rate limit) meets the same actions at
 * submission, whose refusal is anchored under the field with no global error, and nothing is stored.
 * One of the checks is written in JavaScript (the samples' minimum-words action, a hidden.execute view the
 * engine renders through the library's helpers): the only thing exercising that render chain end to end.
 */
describe('Actions - 73 Field actions as the visitor fills the form', () => {
	useFormidableSite();

	before(() => {
		// The site created by useFormidableSite knows the core modules only: the JavaScript sample action
		// is offered on a site once its module is enabled there.
		enableModule(SAMPLE_MODULE, FORMIDABLE_TEST_SITE.key);
	});

	after(() => {
		// The configuration is instance-global: back to the shipped default.
		setRateLimit('30');
	});

	it('asks at blur, blocks a refused value, lets a corrected one through, and asks again before sending', () => {
		createPublishedLiveFormPage(
			'field-actions-live-form',
			'Field Actions Live Form',
			[
				withFieldActions(getInputTextNode({name: 'firstName', title: 'First name'}), [
					getBlockedWordsFieldActionNode({
						name: 'noSpam',
						words: ['spam', 'viagra'],
						rejectionMessage: '<b>${value}</b> is not welcome here.'
					})
				]),
				withFieldActions(getInputEmailNode({name: 'email', title: 'Email'}), [
					getBlockedWordsFieldActionNode({name: 'noNowhere', words: ['nowhere'], trigger: 'submit'})
				]),
				withFieldActions(getTextareaNode({name: 'comment', title: 'Comment'}), [
					getBlockedWordsFieldActionNode({
						name: 'maybe',
						words: ['maybe'],
						severity: 'warn',
						rejectionMessage: 'Are you sure about <i>${value}</i>?'
					})
				]),
				withFieldActions(getTextareaNode({name: 'story', title: 'Your story'}), [
					getMinimumWordsFieldActionNode({
						name: 'threeWords',
						minimumWords: 3,
						rejectionMessage: 'Tell us a little more: at least three words.'
					})
				])
			],
			undefined,
			undefined,
			{actions: [getSaveToJcrActionNode()]}
		).then(({livePath, formName}) => {
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`).as('check');
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submit');

			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();

			// A blocked word, the field left: one request about that value, the message under the field.
			form.getTextInput('firstName').get().type('spam').blur();
			cy.wait('@check').then(({request, response}) => {
				expect(request.body).to.deep.equal({field: 'firstName', value: 'spam', trigger: 'blur'});
				expect(response?.body.verdict).to.equal('reject');
			});
			cy.get('[data-fmdb-node-name="firstName"] .fmdb-validation-error')
				.should('be.visible')
				.and('contain.html', '<b>spam</b> is not welcome here.');
			cy.get('[data-fmdb-node-name="firstName"]').should('not.have.attr', 'aria-busy');
			form.getTextInput('firstName').get().should('have.class', 'fmdb-invalid');

			// The submission is blocked in the browser: no request leaves, the field keeps the focus.
			form.getEmailInput('email').get().type('ada@example.com');
			form.submit();
			cy.get('@submit.all').should('have.length', 0);
			cy.get('[data-fmdb-node-name="firstName"] .fmdb-validation-error').should('be.visible');
			cy.focused().should('have.attr', 'name', 'firstName');

			// Typing lifts the block; leaving the field with a clean value clears the message.
			form.getTextInput('firstName').get().clear().type('Ada');
			cy.get('[data-fmdb-node-name="firstName"] .fmdb-validation-error').should('not.exist');
			form.getTextInput('firstName').get().blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			cy.get('[data-fmdb-node-name="firstName"] .fmdb-validation-error').should('not.exist');

			// A warn-only action advises and blocks nothing.
			form.getTextarea('comment').get().type('maybe later').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'advice');
			cy.get('[data-fmdb-node-name="comment"] .fmdb-validation-warning')
				.should('be.visible')
				.and('contain.html', 'Are you sure about <i>maybe later</i>?');
			cy.get('[data-fmdb-node-name="comment"] .fmdb-validation-error').should('not.exist');
			form.getTextarea('comment').get().should('not.have.class', 'fmdb-invalid');

			// The JavaScript check, rendered by the engine as a view: refused under three words, accepted from three.
			form.getTextarea('story').get().type('too short').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'reject');
			cy.get('[data-fmdb-node-name="story"] .fmdb-validation-error')
				.should('be.visible')
				.and('contain.text', 'Tell us a little more: at least three words.');
			form.getTextarea('story').get().clear().type('a bit more than that').blur();
			cy.wait('@check').its('response.body.verdict').should('equal', 'accept');
			cy.get('[data-fmdb-node-name="story"] .fmdb-validation-error').should('not.exist');

			// The email field waits for the submission: leaving it asked nothing (five checks so far).
			cy.get('@check.all').should('have.length', 5);

			// Before the request leaves, every field with actions is asked with the submit trigger — the
			// blur-checked ones again, the submit-only one for the first time — then the submission goes.
			form.submit();
			cy.wait(['@check', '@check', '@check', '@check']).then(interceptions => {
				const asked = interceptions.map(({request}) => request.body);
				expect(asked.map(body => body.trigger)).to.deep.equal(['submit', 'submit', 'submit', 'submit']);
				expect(asked.map(body => body.field).sort()).to.deep.equal(['comment', 'email', 'firstName', 'story']);
			});
			cy.wait('@submit').its('response.statusCode').should('equal', 200);
			form.getSuccessMessage().should('be.visible');

			// The pipeline is the authority. A browser whose pre-check is stubbed to accept meets the
			// same actions at submission: the refusal comes back anchored under the field, with no
			// global error message, and nothing new is stored.
			getLatestLiveFormSubmission(formName).then(({path: storedBefore}) => {
				cy.intercept('POST', `**${FIELD_ACTION_PATH}*`, {statusCode: 200, body: {verdict: 'accept', messages: []}}).as('stubbedCheck');
				const again = visitLiveForm(livePath);
				again.waitUntilHydrated();
				again.getTextInput('firstName').get().type('viagra').blur();
				cy.wait('@stubbedCheck');
				again.getEmailInput('email').get().type('ada@example.com');
				again.submit();
				cy.wait('@submit').then(({response}) => {
					expect(response?.statusCode).to.equal(422);
					expect(response?.body.errorCode).to.equal('FMDB-015');
				});
				cy.get('[data-fmdb-node-name="firstName"] .fmdb-validation-error')
					.should('be.visible')
					.and('contain.html', '<b>viagra</b> is not welcome here.');
				cy.focused().should('have.attr', 'name', 'firstName');
				again.getErrorMessage().should('not.exist');
				again.shouldBeVisible();
				again.getTextInput('firstName').shouldHaveValue('viagra');
				getLatestLiveFormSubmission(formName).its('path').should('equal', storedBefore);
			});
		});
	});

	it('shows nothing and blocks nothing when the pre-check is rate-limited; the submission is refused anyway', () => {
		createPublishedLiveFormPage('field-actions-limited-form', 'Field Actions Limited Form', [
			withFieldActions(getInputTextNode({name: 'firstName', title: 'First name'}), [
				getBlockedWordsFieldActionNode({name: 'noSpam', words: ['spam']})
			])
		], undefined, undefined, {actions: [getSaveToJcrActionNode()]}).then(({livePath}) => {
			setRateLimit('1');
			cy.intercept('POST', `**${FIELD_ACTION_PATH}*`).as('check');
			cy.intercept('POST', `**${DIRECT_SUBMIT_PATH}*`).as('submit');

			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();
			// Two values in a row: whatever the first answers within the minute's window, the second is
			// over the limit — and typing the second lifted whatever the first had shown.
			form.getTextInput('firstName').get().type('spam').blur();
			cy.wait('@check');
			form.getTextInput('firstName').get().clear().type('spam again').blur();
			cy.wait('@check').its('response.statusCode').should('equal', 429);
			cy.get('[data-fmdb-node-name="firstName"] .fmdb-validation-error').should('not.exist');
			form.getTextInput('firstName').get().should('not.have.class', 'fmdb-invalid');

			// The settle before sending is refused too, so the request leaves — and the pipeline refuses it.
			form.submit();
			cy.wait('@check').its('response.statusCode').should('equal', 429);
			cy.wait('@submit').its('response.body.errorCode').should('equal', 'FMDB-015');
			cy.get('[data-fmdb-node-name="firstName"] .fmdb-validation-error').should('be.visible');
			form.getErrorMessage().should('not.exist');
		});
	});
});
