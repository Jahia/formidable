import {
	FIELDSET_PROFILE,
	getFieldsetNode,
	getInputEmailNode,
	getInputTextNode,
	INPUT_EMAIL_COMPLETE,
	INPUT_TEXT_COMPLETE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 27 Fieldset', () => {
	useFormidableSite();

	it('groups several fields under the same fieldset in live mode', () => {
		createPublishedLiveFormPage(
			'fieldset-form',
			'Fieldset Form',
			[
				getFieldsetNode({
					...FIELDSET_PROFILE,
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
				.shouldHaveLegend(FIELDSET_PROFILE.title!);

			fieldset.getTextInput(INPUT_TEXT_COMPLETE.name!)
				.shouldBeVisible()
				.shouldHaveValue('AB-1234');

			fieldset.getEmailInput(INPUT_EMAIL_COMPLETE.name!)
				.shouldBeVisible()
				.shouldHaveValue('grouped@example.com');
		});
	});
});
