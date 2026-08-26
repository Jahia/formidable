import {getSwitchNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 214 Switch (extended-inputs)', () => {
	useFormidableSite();

	it('renders the default toggle mode as a single checkbox and toggles it', () => {
		createPublishedLiveFormPage(
			'switch-toggle-form',
			'Switch Toggle Form',
			[getSwitchNode({name: 'newsletter', title: 'Subscribe to the newsletter'})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('.fmdbext-switch').as('switch').should('be.visible');
			cy.get('@switch').find('input[name="newsletter"]').should('have.length', 1)
				.and('have.attr', 'type', 'checkbox')
				.and('have.value', 'true')
				.and('not.be.checked');

			cy.get('@switch').find('.fmdbext-switch-text').should('contain.text', 'Subscribe to the newsletter');
			// Default state labels come from the resource bundle.
			cy.get('@switch').find('.fmdbext-switch-state-on').should('contain.text', 'Yes');
			cy.get('@switch').find('.fmdbext-switch-state-off').should('contain.text', 'No');

			// The checkbox is visually hidden behind the styled track → force.
			cy.get('@switch').find('input[name="newsletter"]').check({force: true});
			cy.get('@switch').find('input[name="newsletter"]').should('be.checked');
			cy.get('@switch').find('input[name="newsletter"]').uncheck({force: true});
			cy.get('@switch').find('input[name="newsletter"]').should('not.be.checked');
		});
	});

	it('renders the buttons mode as an explicit yes/no radio pair', () => {
		createPublishedLiveFormPage(
			'switch-buttons-form',
			'Switch Buttons Form',
			[getSwitchNode({
				name: 'attending',
				title: 'Will you attend?',
				displayMode: 'buttons',
				onLabel: 'Attending',
				offLabel: 'Not attending',
				required: true
			})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('fieldset.fmdbext-switch').as('switch').should('be.visible');
			cy.get('@switch').find('legend').should('contain.text', 'Will you attend?')
				.find('.fmdb-required-indicator').should('exist');

			// Two radios: both states are explicit so "answered no" differs from "not answered".
			cy.get('@switch').find('input[name="attending"]').should('have.length', 2)
				.each($input => expect($input.attr('type')).to.equal('radio'));
			cy.get('@switch').find('.fmdbext-switch-button-label').should('contain.text', 'Attending')
				.and('contain.text', 'Not attending');

			cy.get('@switch').find('input[name="attending"][value="false"]').check({force: true});
			cy.get('@switch').find('input[name="attending"][value="false"]').should('be.checked');
			cy.get('@switch').find('input[name="attending"][value="true"]').should('not.be.checked');
		});
	});

	it('prechecks the toggle when defaultState is true', () => {
		createPublishedLiveFormPage(
			'switch-default-form',
			'Switch Default Form',
			[getSwitchNode({name: 'optin', title: 'Opt in', defaultState: true})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('input[name="optin"]').should('be.checked');
		});
	});

	it('keeps the buttons mode unanswered when defaultState is false', () => {
		// A stored defaultState=false (any editor save writes it) must NOT precheck
		// "No": the question stays unanswered so 'required' still means "pick one".
		createPublishedLiveFormPage(
			'switch-buttons-default-form',
			'Switch Buttons Default Form',
			[
				getSwitchNode({name: 'unanswered', title: 'Unanswered', displayMode: 'buttons', defaultState: false}),
				getSwitchNode({name: 'preanswered', title: 'Preanswered', displayMode: 'buttons', defaultState: true})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('input[name="unanswered"]:checked').should('have.length', 0);
			form.get().find('input[name="preanswered"][value="true"]').should('be.checked');
		});
	});
});
