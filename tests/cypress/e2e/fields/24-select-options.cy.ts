import {
	getSelectNode,
	SELECT_DISABLED,
	SELECT_EMPTY_LABEL,
	SELECT_MULTIPLE,
	SELECT_MULTIPLE_EMPTY_LABEL,
	SELECT_SINGLE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 24 Select options', () => {
	useFormidableSite();

	it('renders visible options, single and multiple select, and disabled state', () => {
		createPublishedLiveFormPage(
			'select-options-form',
			'Select Options Form',
			[
				getSelectNode(SELECT_SINGLE),
				getSelectNode(SELECT_MULTIPLE),
				getSelectNode(SELECT_DISABLED)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.getSelectInput(SELECT_SINGLE.name!)
				.shouldBeVisible()
				.shouldHaveOptionCount(4)
				.shouldHaveSelectedOption('Please select')
				.select('Support')
				.shouldHaveSelectedOption('Support');

			form.getSelectInput(SELECT_MULTIPLE.name!)
				.shouldBeVisible()
				.shouldBeMultiple()
				.shouldHaveSize(4)
				.shouldHaveSelectedOptions(['EMEA', 'LATAM']);

			form.getSelectInput(SELECT_DISABLED.name!)
				.shouldBeVisible()
				.shouldBeDisabled()
				.shouldHaveOptionCount(2)
				.shouldHaveSelectedOption('Closed');
		});
	});

	it('renders the configured empty option first on a single select, never on a multiple one', () => {
		createPublishedLiveFormPage(
			'select-empty-label-form',
			'Select Empty Label Form',
			[
				getSelectNode(SELECT_EMPTY_LABEL),
				getSelectNode(SELECT_MULTIPLE_EMPTY_LABEL)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// The empty option leads the list, value-less and selected by default,
			// so the field starts empty and required validation stays effective.
			const single = form.getSelectInput(SELECT_EMPTY_LABEL.name!);
			single.shouldBeVisible()
				.shouldHaveOptionCount(3)
				.shouldHaveSelectedOption('Choose a contract type');
			single.getOptions().first()
				.should('have.text', 'Choose a contract type')
				.should('have.attr', 'value', '');

			// The same configuration on a multiple select renders no empty option.
			form.getSelectInput(SELECT_MULTIPLE_EMPTY_LABEL.name!)
				.shouldBeVisible()
				.shouldBeMultiple()
				.shouldHaveOptionCount(2);
		});
	});
});
