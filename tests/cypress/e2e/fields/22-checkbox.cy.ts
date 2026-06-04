import {
	CHECKBOX_GROUP_COMPLETE,
	CHECKBOX_SINGLE_COMPLETE,
	getCheckboxNode
} from '../../support/fixtures';
import {createFormNode, getFormPreview} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 22 Checkbox', () => {
	useFormidableSite();

	it('renders and toggles a required standalone checkbox', () => {
		createFormNode('checkbox-single-form', 'Checkbox Single Form', [getCheckboxNode(CHECKBOX_SINGLE_COMPLETE)]);

		const form = getFormPreview('Checkbox Single Form');
		const checkbox = form.getCheckbox(CHECKBOX_SINGLE_COMPLETE.name!);

		checkbox
			.shouldBeVisible()
			.shouldHaveLabel(CHECKBOX_SINGLE_COMPLETE.choices[0].label)
			.shouldBeRequired()
			.shouldBeChecked()
			.uncheck()
			.shouldBeUnchecked()
			.check()
			.shouldBeChecked();
	});

	it('renders and toggles a required checkbox group', () => {
		createFormNode('checkbox-group-form', 'Checkbox Group Form', [getCheckboxNode(CHECKBOX_GROUP_COMPLETE)]);

		const form = getFormPreview('Checkbox Group Form');
		const group = form.getCheckboxGroup(CHECKBOX_GROUP_COMPLETE.name!);

		group
			.shouldBeVisible()
			.shouldHaveLegend(CHECKBOX_GROUP_COMPLETE.title!)
			.shouldBeRequired()
			.checkByLabels(['Reading', 'Music']);

		group.getCheckbox('Reading').shouldBeChecked();
		group.getCheckbox('Music').shouldBeChecked();
	});
});
