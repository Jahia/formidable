import {
	createPublishedLiveFormPage,
	getInputEmailNode,
	getInputTextNode,
	getTextareaNode,
	visitLiveForm,
	withValidationMessages
} from '../../support/fixtures';
import {useFormidableSite} from './support';

const RESET_TEXTAREA = {
	name: 'resettableSummary',
	title: 'Resettable summary',
	required: true,
	rows: 4
};

const RESET_MESSAGE = 'Provide a summary before resetting.';

const FIRST_FIELD = {
	name: 'focusFirstName',
	title: 'First name',
	required: true
};

const FIRST_MESSAGE = 'First name is required.';

const SECOND_FIELD = {
	name: 'focusEmail',
	title: 'Email',
	required: true
};

const SECOND_MESSAGE = 'Email is required.';

describe('Validation - 33 Validation flow', () => {
	useFormidableSite();

	it('clears inline validation errors and invalid styling on form reset', () => {
		createPublishedLiveFormPage(
			'validation-reset-form',
			'Validation Reset Form',
			[
				withValidationMessages(getTextareaNode(RESET_TEXTAREA), {msgValueMissing: RESET_MESSAGE})
			],
			'validation-reset-form-page',
			'Validation Reset Form',
			{
				properties: [
					{name: 'showResetBtn', value: 'true', type: 'BOOLEAN'}
				]
			}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const textarea = form.getTextarea(RESET_TEXTAREA.name);

			form.submit();

			textarea
				.shouldHaveValidationError(RESET_MESSAGE)
				.shouldBeMarkedInvalid();

			form.reset();

			textarea
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});

	it('focuses the first invalid field, then moves focus to the next invalid field after correction', () => {
		createPublishedLiveFormPage(
			'validation-focus-form',
			'Validation Focus Form',
			[
				withValidationMessages(
					getInputTextNode(FIRST_FIELD),
					{msgValueMissing: FIRST_MESSAGE}
				),
				withValidationMessages(
					getInputEmailNode(SECOND_FIELD),
					{msgValueMissing: SECOND_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const firstInput = form.getTextInput(FIRST_FIELD.name);
			const secondInput = form.getEmailInput(SECOND_FIELD.name);

			form.submit();

			firstInput
				.shouldHaveValidationError(FIRST_MESSAGE)
				.shouldBeMarkedInvalid()
				.shouldBeFocused();

			secondInput
				.shouldHaveValidationError(SECOND_MESSAGE)
				.shouldBeMarkedInvalid();

			firstInput
				.type('John')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();

			form.submit();

			secondInput
				.shouldHaveValidationError(SECOND_MESSAGE)
				.shouldBeFocused();

			firstInput
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});
});
