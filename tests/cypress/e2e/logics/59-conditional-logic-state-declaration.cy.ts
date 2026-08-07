import {createPublishedLiveFormPage, getInputTextNode} from '../../support/fixtures';
import {visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

const SAVE_TO_JCR_ACTION = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: [] as Array<{name: string; value: string}>
};

/**
 * The browser declares the provider state it saw in the X-Formidable-Logic-State header
 * at submit time, which lets the server evaluate provider rules coherently instead of
 * counting every provider-gated field as hidden. From the visitor's side nothing changes:
 * these tests prove the declaration rides along transparently on honest submissions.
 */
describe('Form logic - 59 Provider state declaration at submit', () => {
	useFormidableSite();

	const cookieRule = JSON.stringify({
		logicId: 'decl-cookie',
		sourceType: 'cookie',
		cookie: 'fmdb-consent',
		operator: 'exists'
	});

	const createFormPage = (suffix: string, extras: object = {}) => {
		// Append the rule to the fixture-built properties: replacing them would silently
		// drop what the extras produced (required, constraints…).
		const gated = getInputTextNode({name: 'preferences', title: 'preferences', ...extras});
		gated.properties = [...(gated.properties ?? []), {name: 'logics', values: [cookieRule]}];

		return createPublishedLiveFormPage(
			`declaration-live-${suffix}-${Date.now()}`,
			'Declaration live form',
			[getInputTextNode({name: 'fullname', title: 'fullname'}), gated],
			undefined,
			undefined,
			{actions: [SAVE_TO_JCR_ACTION]}
		);
	};

	it('submits a visible provider-gated field and its value is accepted', () => {
		createFormPage('visible').then(({livePath}) => {
			cy.setCookie('fmdb-consent', 'yes');
			const form = visitLiveForm(livePath);

			cy.get('[data-fmdb-node-name="preferences"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'false');

			form.getTextInput('fullname').type('Alice');
			form.getTextInput('preferences').type('newsletter');
			form.submit();

			// The declaration matches the visible field, so the server accepts the value:
			// an honest browser flow must be indistinguishable from before.
			form.getSuccessMessage().should('be.visible');
		});
	});

	it('submits with the gated field hidden and the submission still succeeds', () => {
		createFormPage('hidden').then(({livePath}) => {
			cy.clearCookie('fmdb-consent');
			const form = visitLiveForm(livePath);

			cy.get('[data-fmdb-node-name="preferences"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true');

			form.getTextInput('fullname').type('Bob');
			form.submit();

			// Hidden field → disabled controls → no value submitted; the declaration says
			// "cookie absent", which is coherent with that.
			form.getSuccessMessage().should('be.visible');
		});
	});

	it('enforces required on a provider-gated field once it is visible', () => {
		createFormPage('required', {required: true}).then(({livePath}) => {
			cy.setCookie('fmdb-consent', 'yes');
			const form = visitLiveForm(livePath);

			cy.get('[data-fmdb-node-name="preferences"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'false');

			form.getTextInput('fullname').type('Carol');
			form.submit();

			// The browser blocks first (client-side required): the field is visible, so
			// its requiredness is active end to end — the server would answer FMDB-010
			// to a client that skipped this check, as covered by the direct HTTP spec.
			form.getTextInput('preferences').shouldBeInvalid();
		});
	});
});
