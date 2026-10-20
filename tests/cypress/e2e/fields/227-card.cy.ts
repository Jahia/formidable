import {getNodeByPath} from '@jahia/cypress';
import {getCardNode, getInputTextNode, getLatestLiveFormSubmission} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {expectErrorResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from './support';

interface StoredValueResponse {
	data?: {jcr?: {nodeByPath?: {properties?: Array<{name: string; value?: string | null}>}}};
}

const SAVE_TO_JCR_ACTION = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: [] as Array<{name: string; value: string}>
};

const CONTACT_REASON = getCardNode({
	name: 'contactReason',
	title: 'Why are you contacting us?',
	required: true,
	choices: [
		{value: 'sales', label: 'Sales'},
		{value: 'support', label: 'Support'},
		{value: 'press', label: 'Press'}
	]
});

describe('Form fields - 227 Card (extended-inputs)', () => {
	useFormidableSite();

	let formId: string;
	let livePath: string;

	before(() => {
		cy.login();
		createPublishedLiveFormPage(
			'card-form',
			'Card Form',
			[CONTACT_REASON],
			'card-form-page',
			'Card form page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(result => {
			formId = result.formId;
			livePath = result.livePath;
		});
		cy.logout();
	});

	it('renders one card per option, as a radio group that says one answer is enough', () => {
		const form = visitLiveForm(livePath);

		form.get().find('fieldset.fmdbext-card').as('card').should('be.visible')
			.and('have.attr', 'role', 'radiogroup')
			.and('have.attr', 'data-fmdb-advance-on-change', 'true');
		cy.get('@card').find('legend').should('contain.text', 'Why are you contacting us?')
			.find('.fmdb-required-indicator').should('exist');
		cy.get('@card').find('input[name="contactReason"]').should('have.length', 3)
			.each($input => expect($input.attr('type')).to.equal('radio'));
		cy.get('@card').find('.fmdbext-card-label').should('have.length', 3)
			.first().should('contain.text', 'Sales');
	});

	it('picks a card with a click on it, and another with the arrow keys', () => {
		const form = visitLiveForm(livePath);
		form.waitUntilHydrated();

		form.get().find('.fmdbext-card-label').contains('Support').click();
		form.get().find('input[name="contactReason"][value="support"]').should('be.checked');

		// The radios are visually hidden behind their cards; the keyboard still moves the selection.
		form.get().find('input[name="contactReason"][value="support"]').focus();
		cy.realPress('ArrowRight');
		form.get().find('input[name="contactReason"][value="press"]').should('be.checked');
		form.get().find('input[name="contactReason"][value="support"]').should('not.be.checked');
	});

	it('shows the required message in the group when no card is picked', () => {
		const form = visitLiveForm(livePath);
		form.submit();

		form.get().find('fieldset.fmdbext-card .fmdb-validation-error').should('be.visible');
		form.get().find('.fmdbext-card-item .fmdb-validation-error').should('not.exist');
	});

	it('submits the value of the picked card', () => {
		const form = visitLiveForm(livePath);
		form.get().find('.fmdbext-card-label').contains('Press').click();
		form.submit();
		form.waitForSubmit();

		getLatestLiveFormSubmission('card-form').then(({path}) => {
			getNodeByPath(`${path}/data`, ['contactReason'], '', [], 'LIVE')
				.then((response: StoredValueResponse) => {
					const stored = response.data?.jcr?.nodeByPath?.properties?.find(p => p.name === 'contactReason');
					expect(stored?.value).to.eq('press');
				});
		});
	});

	it('drives conditional logic: the press card shows the outlet field, and the submission passes', () => {
		const outlet = getInputTextNode({name: 'outlet', title: 'Your media outlet'});
		outlet.properties = [...(outlet.properties ?? []), {name: 'logics', values: [JSON.stringify({
			logicId: 'card-press',
			sourceFieldName: 'contactReason',
			sourceFieldType: 'fmdbext:card',
			valueKind: 'choice',
			operator: 'in',
			values: ['press']
		})]}];

		createPublishedLiveFormPage(
			'card-logic-form',
			'Card Logic Form',
			[CONTACT_REASON, outlet],
			'card-logic-form-page',
			'Card logic form page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(result => {
			const form = visitLiveForm(result.livePath);
			form.waitUntilHydrated();

			cy.get('[data-fmdb-node-name="outlet"]').should('have.attr', 'data-fmdb-logic-hidden', 'true');
			form.get().find('.fmdbext-card-label').contains('Press').click();
			cy.get('[data-fmdb-node-name="outlet"]').should('not.have.attr', 'data-fmdb-logic-hidden', 'true');
			form.getTextInput('outlet').shouldBeVisible().type('Daily Planet');

			form.submit();
			form.getSuccessMessage().should('be.visible');
			getLatestLiveFormSubmission('card-logic-form').then(({path}) => {
				getNodeByPath(`${path}/data`, ['contactReason', 'outlet'], '', [], 'LIVE')
					.then((response: StoredValueResponse) => {
						const properties = response.data?.jcr?.nodeByPath?.properties ?? [];
						expect(properties.find(p => p.name === 'contactReason')?.value).to.eq('press');
						expect(properties.find(p => p.name === 'outlet')?.value).to.eq('Daily Planet');
					});
			});
		});
	});

	it('rejects a value that is not one of the cards', () => {
		cy.logout();
		postDirectMultipartSubmission({
			formId,
			fields: {contactReason: 'careers'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});
});
