import {enableModule} from '@jahia/cypress';
import {
	createPublishedLiveFormPage,
	FORMIDABLE_TEST_SITE,
	getCardNode,
	getCheckboxNode,
	getInputTextNode,
	getRatingNode,
	getScaleNode,
	getSelectNode,
	visitLiveForm
} from '../../support/fixtures';
import type {JahiaNode} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * The contract of a field type in One-at-a-time (docs/architecture/one-at-a-time.md): a view that puts
 * data-fmdb-advance-on-change on the element holding its controls moves the form on with the visitor's
 * pick. Declared by the card, the rating and the scale of formidable-extended-inputs, and by
 * fmdbsample:quickConfirm, a checkbox of the tsx samples module — a field type of another module, on a
 * control that is not a radio button. A field that takes several answers, or a select, does not declare it.
 */
const SAMPLE_MODULE = 'formidable-test-module-samples-tsx';

/** The visitor's click on the label of a choice, as a pointer does it. */
const pick = (name: string, value: string) => {
	cy.get(`input[name="${name}"][value="${value}"]`).invoke('attr', 'id').then(id => {
		cy.get(`label[for="${id}"]`).realClick();
	});
};

const quickConfirm = (name: string, title: string, required: boolean): JahiaNode => ({
	name,
	primaryNodeType: 'fmdbsample:quickConfirm',
	properties: [
		{name: 'jcr:title', value: title, language: 'en'},
		{name: 'required', value: String(required), type: 'BOOLEAN'}
	]
});

describe('Validation - 49 One-at-a-time fields that move on with a pick', () => {
	useFormidableSite();

	before(() => {
		enableModule(SAMPLE_MODULE, FORMIDABLE_TEST_SITE.key);
	});

	it('moves on with a pick in a card, a rating, a scale and a field of another module, and not in a group or a select', () => {
		createPublishedLiveFormPage('oaat-pick-fields', 'oaat pick fields', [
			getCardNode({
				name: 'profile',
				title: 'You are',
				required: true,
				choices: [{value: 'customer', label: 'A customer'}, {value: 'supplier', label: 'A supplier'}]
			}),
			getRatingNode({name: 'satisfaction', title: 'Satisfaction', maxValue: 5, icon: 'number'}),
			getScaleNode({name: 'agreement', title: 'Agreement', minValue: 0, maxValue: 10}),
			quickConfirm('terms', 'I accept the terms', true),
			getCheckboxNode({
				name: 'interests',
				title: 'Interests',
				choices: [{value: 'music', label: 'Music'}, {value: 'sport', label: 'Sport'}]
			}),
			getSelectNode({
				name: 'country',
				title: 'Country',
				options: [{value: 'fr', label: 'France'}, {value: 'ch', label: 'Switzerland'}]
			}),
			getInputTextNode({name: 'closing', title: 'Closing remark'})
		], undefined, undefined, {properties: [{name: 'formExperience', value: 'oneAtATime'}]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// The card, the rating and the scale move on with a pick
			form.shouldShowScreenOf('profile');
			form.get().find('.fmdbext-card-label').contains('A supplier').realClick();
			form.shouldShowScreenOf('satisfaction');
			pick('satisfaction', '4');
			form.shouldShowScreenOf('agreement');
			pick('agreement', '8');
			form.shouldShowScreenOf('terms');

			// The field of another module: the checkbox moves on once checked
			form.get().find('[data-fmdb-node-name="terms"] [data-fmdb-advance-on-change="true"]')
				.should('have.attr', 'aria-describedby').and('contain', 'fmdb-advance-hint-');
			form.get().find('[data-fmdb-node-name="terms"] label').realClick();
			form.shouldShowScreenOf('interests');

			// Back to it: unchecking asks for the next screen too, and the required box refuses it
			form.previousStep();
			form.shouldShowScreenOf('terms');
			form.get().find('[data-fmdb-node-name="terms"] label').realClick();
			cy.wait(600);
			form.shouldShowScreenOf('terms');
			form.get().find('input[name="terms"]').should('not.be.checked');
			form.get().find('[data-fmdb-node-name="terms"] label').realClick();
			form.shouldShowScreenOf('interests');

			// A group takes several answers: a pick does not move on, Next does
			form.get().find('[data-fmdb-node-name="interests"] label').contains('Music').realClick();
			cy.wait(600);
			form.shouldShowScreenOf('interests');
			form.nextStep();

			// Nor does a select, whose arrow keys change the value
			form.shouldShowScreenOf('country');
			form.get().find('select[name="country"]').select('Switzerland');
			cy.wait(600);
			form.shouldShowScreenOf('country');
			form.nextStep();
			form.shouldShowScreenOf('closing');
		});
	});
});
