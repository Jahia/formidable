import {
	FIELDSET_PROFILE,
	getCheckboxNode,
	getFieldsetNode,
	getInputEmailNode,
	getInputTextNode,
	getRadioNode,
	INPUT_EMAIL_COMPLETE,
	INPUT_TEXT_COMPLETE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

/** A matrix: groups that share their options, one question per row, the options across. */
const MATRIX = {
	name: 'satisfaction',
	title: 'How satisfied are you with',
	helpText: 'One answer per line',
	columns: ['Poor', 'Fair', 'Good'],
	rows: [
		{name: 'price', title: 'The price'},
		// a help text sits between the legend and the options: the row must keep its columns
		{name: 'service', title: 'The service', helpText: 'Compared with last year'},
		{name: 'delivery', title: 'The delivery'}
	],
	extras: {name: 'extras', title: 'The extras, several answers'}
};
const matrixChoices = () => MATRIX.columns.map(label => ({value: label.toLowerCase(), label}));

describe('Form fields - 27 Fieldset', () => {
	useFormidableSite();

	it('lays out groups that share their options as a matrix, the questions down, the options across', () => {
		createPublishedLiveFormPage(
			'matrix-form',
			'Matrix Form',
			[
				getFieldsetNode({
					...MATRIX,
					view: 'matrix',
					children: [
						...MATRIX.rows.map(row => getRadioNode({...row, choices: matrixChoices()})),
						getCheckboxNode({...MATRIX.extras, choices: matrixChoices()})
					]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fieldset = form.getFieldset(MATRIX.title);
			fieldset.shouldBeVisible().shouldHaveLegend(MATRIX.title).shouldHaveHelpText(MATRIX.helpText);
			// the view marks the fieldset and tells the stylesheet how many columns there are
			fieldset.get().should('have.class', 'fmdb-matrix')
				.invoke('attr', 'style').should('match', /--fmdb-matrix-columns:\s*3/);
			// the help of the fieldset comes before the heading row, under the legend
			fieldset.getHelpText().next().should('have.class', 'fmdb-matrix-header');
			// one heading row, for the eye only: the inputs keep their own labels
			fieldset.get().find('.fmdb-matrix-header').should('have.attr', 'aria-hidden', 'true')
				.find('.fmdb-matrix-column').should('have.length', MATRIX.columns.length)
				.then($columns => expect([...$columns].map(column => column.textContent)).to.deep.equal(MATRIX.columns));
			// every row stays a group of its own: its legend, its labelled options, laid out as a grid
			MATRIX.rows.forEach(row => {
				const group = fieldset.getRadioGroup(row.name);
				group.shouldHaveLegend(row.title);
				group.get().should('have.css', 'display', 'grid');
				// the option labels stay in the markup, clipped to one pixel (Cypress counts a clipped element as visible)
				group.get().find('label.fmdb-radio-label').should('have.length', MATRIX.columns.length).first()
					.then($label => expect($label[0].getBoundingClientRect().width, 'label clipped out of sight').to.be.at.most(1));
			});
			fieldset.getCheckboxGroup(MATRIX.extras.name).shouldHaveLegend(MATRIX.extras.title)
				.get().should('have.css', 'display', 'grid');
			fieldset.getRadioGroup('service').shouldHaveHelpText('Compared with last year');
			// the cells line up: the first option of every row starts at the same x, the row with a help text included
			fieldset.get().find(':is(.fmdb-radio-group, .fmdb-checkbox-group) .fmdb-group-item:first-child input').then($inputs => {
				const lefts = new Set([...$inputs].map(input => Math.round(input.getBoundingClientRect().left)));
				expect($inputs, 'one first cell per row').to.have.length(MATRIX.rows.length + 1);
				expect([...lefts], 'the first column').to.have.length(1);
			});
			// … and under the heading of their column
			fieldset.get().find('.fmdb-matrix-column').first().then($heading => {
				const centre = (element: Element) => Math.round(element.getBoundingClientRect().left + element.getBoundingClientRect().width / 2);
				fieldset.get().find('.fmdb-radio-group .fmdb-group-item:first-child input').then($inputs => {
					[...$inputs].forEach(input => expect(Math.abs(centre(input) - centre($heading[0])), 'centred under Poor').to.be.at.most(2));
				});
			});
			// each question is answered on its own
			fieldset.getRadioGroup('price').select('Good');
			fieldset.getRadioGroup('service').select('Poor');
			fieldset.getRadioGroup('price').shouldHaveSelected('Good');
			fieldset.getRadioGroup('service').shouldHaveSelected('Poor');
			fieldset.getCheckboxGroup(MATRIX.extras.name).checkByLabels(['Fair', 'Good']);
			fieldset.getCheckboxGroup(MATRIX.extras.name).getCheckbox('Fair').shouldBeChecked();
			fieldset.getCheckboxGroup(MATRIX.extras.name).getCheckbox('Poor').shouldBeUnchecked();
		});
	});

	it('keeps the default layout under the matrix view when the fields cannot form a matrix', () => {
		createPublishedLiveFormPage(
			'matrix-fallback-form',
			'Matrix Fallback Form',
			[
				getFieldsetNode({
					...FIELDSET_PROFILE,
					view: 'matrix',
					children: [
						getRadioNode({...MATRIX.rows[0], choices: matrixChoices()}),
						getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: 'not a row'})
					]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fieldset = form.getFieldset(FIELDSET_PROFILE.title!);
			fieldset.shouldBeVisible().shouldHaveLegend(FIELDSET_PROFILE.title!).shouldNotHaveHelpText();
			fieldset.get().should('not.have.class', 'fmdb-matrix').find('.fmdb-matrix-header').should('not.exist');
			fieldset.getRadioGroup(MATRIX.rows[0].name).get().should('not.have.css', 'display', 'grid')
				.find('label.fmdb-radio-label').first().should('be.visible');
			fieldset.getTextInput(INPUT_TEXT_COMPLETE.name!).shouldBeVisible().shouldHaveValue('not a row');
		});
	});

	it('groups several fields under the same fieldset in live mode, with its own help text under the legend', () => {
		createPublishedLiveFormPage(
			'fieldset-form',
			'Fieldset Form',
			[
				getFieldsetNode({
					...FIELDSET_PROFILE,
					helpText: 'Tell us who you are',
					children: [
						getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: 'AB-1234'}),
						getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: 'grouped@example.com'})
					]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fieldset = form.getFieldset(FIELDSET_PROFILE.title!);

			fieldset
				.shouldBeVisible()
				.shouldHaveLegend(FIELDSET_PROFILE.title!)
				.shouldHaveHelpText('Tell us who you are');
			// the help of the fieldset sits right under the legend, before its fields
			fieldset.getLegend().next().should('have.class', 'fmdb-form-help');

			fieldset.getTextInput(INPUT_TEXT_COMPLETE.name!)
				.shouldBeVisible()
				.shouldHaveValue('AB-1234');

			fieldset.getEmailInput(INPUT_EMAIL_COMPLETE.name!)
				.shouldBeVisible()
				.shouldHaveValue('grouped@example.com');
		});
	});
});
