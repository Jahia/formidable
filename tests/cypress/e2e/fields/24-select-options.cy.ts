import {
	getSelectNode,
	SELECT_DISABLED,
	SELECT_MULTIPLE,
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
				.shouldHaveOptionCount(3)
				.shouldHaveSelectedOption('Engineering')
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
});
