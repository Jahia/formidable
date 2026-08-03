import {getScaleNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 213 Scale (extended-inputs)', () => {
	useFormidableSite();

	it('renders the default 0-10 scale and selects a chip', () => {
		createPublishedLiveFormPage(
			'scale-default-form',
			'Scale Default Form',
			[getScaleNode({
				name: 'agreement',
				title: 'Agreement',
				minLabel: 'Disagree',
				maxLabel: 'Agree'
			})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('fieldset.fmdbext-scale').as('scale').should('be.visible');
			cy.get('@scale').find('legend').should('contain.text', 'Agreement');

			// Defaults are minValue 0 / maxValue 10 / step 1 → eleven chips.
			cy.get('@scale').find('input[name="agreement"]').should('have.length', 11);
			cy.get('@scale').find('input[name="agreement"][value="0"]').should('exist');
			cy.get('@scale').find('input[name="agreement"][value="10"]').should('exist');

			cy.get('@scale').find('.fmdbext-end-labels').should('contain.text', 'Disagree')
				.and('contain.text', 'Agree');

			// Scale inputs are visually hidden (sr-only pattern) behind chip labels → force.
			cy.get('@scale').find('input[name="agreement"][value="8"]').check({force: true});
			cy.get('@scale').find('input[name="agreement"][value="8"]').should('be.checked');
		});
	});

	it('honors custom min, max and step', () => {
		createPublishedLiveFormPage(
			'scale-custom-form',
			'Scale Custom Form',
			[getScaleNode({name: 'odd', title: 'Odd steps', minValue: 1, maxValue: 5, step: 2})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// 1, 3, 5.
			form.get().find('input[name="odd"]').should('have.length', 3);
			form.get().find('input[name="odd"][value="1"]').should('exist');
			form.get().find('input[name="odd"][value="3"]').should('exist');
			form.get().find('input[name="odd"][value="5"]').should('exist');
			form.get().find('input[name="odd"][value="2"]').should('not.exist');
		});
	});

	it('forces the standard 0-10 presentation through the nps view', () => {
		createPublishedLiveFormPage(
			'scale-nps-form',
			'Scale NPS Form',
			// Contributor-set bounds are ignored by the nps view on purpose.
			[getScaleNode({name: 'nps', title: 'Recommend us?', minValue: 2, maxValue: 5, npsView: true})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('input[name="nps"]').should('have.length', 11);
			form.get().find('input[name="nps"][value="0"]').should('exist');
			form.get().find('input[name="nps"][value="10"]').should('exist');
		});
	});
});
