import {getRatingNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 212 Rating (extended-inputs)', () => {
	useFormidableSite();

	it('renders a required star rating with end labels and selects a value', () => {
		createPublishedLiveFormPage(
			'rating-star-form',
			'Rating Star Form',
			[getRatingNode({
				name: 'satisfaction',
				title: 'Satisfaction',
				required: true,
				minLabel: 'Poor',
				maxLabel: 'Excellent'
			})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('fieldset.fmdbext-rating').as('rating').should('be.visible');
			cy.get('@rating').find('legend').should('contain.text', 'Satisfaction')
				.find('.fmdb-required-indicator').should('exist');

			// Default maxValue is 5 → five radios, each labelled value/max for screen readers.
			cy.get('@rating').find('input[name="satisfaction"]').should('have.length', 5)
				.each($input => expect($input.attr('type')).to.equal('radio'));
			cy.get('@rating').find('input[aria-label="3/5"]').should('exist');
			cy.get('@rating').find('.fmdbext-rating-items').should('have.attr', 'data-fmdbext-icon', 'star');

			cy.get('@rating').find('.fmdbext-end-labels').should('contain.text', 'Poor')
				.and('contain.text', 'Excellent');

			// Rating inputs are visually hidden (sr-only pattern) behind icon labels → force.
			cy.get('@rating').find('input[name="satisfaction"][value="4"]').check({force: true});
			cy.get('@rating').find('input[name="satisfaction"][value="4"]').should('be.checked');
			cy.get('@rating').find('input[name="satisfaction"][value="2"]').should('not.be.checked');
		});
	});

	it('renders number chips honoring maxValue and caps it to ten items', () => {
		createPublishedLiveFormPage(
			'rating-number-form',
			'Rating Number Form',
			[
				getRatingNode({name: 'stars7', title: 'Seven chips', icon: 'number', maxValue: 7}),
				getRatingNode({name: 'starsMax', title: 'Capped chips', icon: 'number', maxValue: 15})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('input[name="stars7"]').should('have.length', 7);
			// Number mode prints the value inside the visible chip label; DOM order is
			// natural 1..max so keyboard arrows follow the visual direction.
			form.get().find('input[name="stars7"][value="7"]').should('exist');
			form.get().find('fieldset.fmdbext-rating').first()
				.find('.fmdbext-rating-label').first().should('contain.text', '1');
			form.get().find('fieldset.fmdbext-rating').first()
				.find('.fmdbext-rating-label').last().should('contain.text', '7');

			// maxValue is clamped to the 10-item ceiling.
			form.get().find('input[name="starsMax"]').should('have.length', 10);
		});
	});
});
