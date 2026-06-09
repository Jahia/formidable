import {
	createPublishedLiveFormPage,
	getInputEmailNode,
	getInputTextNode,
	getTextareaNode,
	visitLiveForm,
	withValidationMessages
} from '../../support/fixtures';
import {useFormidableSite} from './support';

const TEXT_FIELD = {
	name: 'employeeCodeValidation',
	title: 'Employee code',
	required: true,
	pattern: '[A-Z]{2}-[0-9]{4}',
	placeholder: 'AB-1234'
};

const TEXT_MESSAGES = {
	msgValueMissing: 'Employee code is mandatory.',
	msgPatternMismatch: 'Use the AB-1234 format.'
};

const TEXTAREA_FIELD = {
	name: 'projectSummaryValidation',
	title: 'Project summary',
	minLength: 10,
	rows: 4
};

const TEXTAREA_MESSAGE = 'Project summary must contain at least 10 characters.';

const MIN_LENGTH_TEXT_FIELD = {
	name: 'teamCodeValidation',
	title: 'Team code',
	minLength: 5,
	placeholder: 'ABCDE'
};

const MIN_LENGTH_TEXT_MESSAGE = 'Team code must contain at least 5 characters.';

const NATIVE_EMAIL_FIELD = {
	name: 'contactEmailValidation',
	title: 'Contact email'
};

const CUSTOM_EMAIL_FIELD = {
	name: 'typeMismatchEmail',
	title: 'Work email'
};

const CUSTOM_TYPE_MISMATCH_MESSAGE = 'Please enter a valid email address (e.g. name@company.com).';

describe('Validation - 31 Textual validation', () => {
	useFormidableSite();

	it('shows custom messages for required, pattern, and tooShort text constraints', () => {
		createPublishedLiveFormPage(
			'validation-textual-form',
			'Validation Textual Form',
			[
				withValidationMessages(getInputTextNode(TEXT_FIELD), TEXT_MESSAGES),
				withValidationMessages(getTextareaNode(TEXTAREA_FIELD), {msgTooShort: TEXTAREA_MESSAGE})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const textInput = form.getTextInput(TEXT_FIELD.name);
			const textarea = form.getTextarea(TEXTAREA_FIELD.name);

			form.submit();

			textInput
				.shouldHaveValidationError(TEXT_MESSAGES.msgValueMissing)
				.shouldBeMarkedInvalid();

			textInput.type('INVALID');
			form.submit();

			textInput
				.shouldHaveValidationError(TEXT_MESSAGES.msgPatternMismatch)
				.shouldBeMarkedInvalid();

			textInput
				.type('AB-1234')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();

			textarea.type('short');
			form.submit();

			textarea
				.shouldHaveValidationError(TEXTAREA_MESSAGE)
				.shouldBeMarkedInvalid();

			textarea
				.type('A long enough summary for validation.')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});

	it('shows a custom tooShort message for a text input with a minLength constraint', () => {
		createPublishedLiveFormPage(
			'validation-text-minlength-form',
			'Validation Text MinLength Form',
			[
				withValidationMessages(
					getInputTextNode(MIN_LENGTH_TEXT_FIELD),
					{msgTooShort: MIN_LENGTH_TEXT_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const textInput = form.getTextInput(MIN_LENGTH_TEXT_FIELD.name);

			textInput.type('ABC');
			form.submit();

			textInput
				.shouldHaveValidationError(MIN_LENGTH_TEXT_MESSAGE)
				.shouldBeMarkedInvalid();

			textInput
				.type('ABCDE')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});

	it('falls back to the native browser message when no email override is configured', () => {
		createPublishedLiveFormPage(
			'validation-native-email-form',
			'Validation Native Email Form',
			[
				getInputEmailNode(NATIVE_EMAIL_FIELD)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const emailInput = form.getEmailInput(NATIVE_EMAIL_FIELD.name);

			emailInput.type('invalid-email');
			form.submit();

			emailInput.getInput().then($input => {
				const input = $input.get(0) as HTMLInputElement;
				expect(input.checkValidity()).to.eq(false);
				expect(input.validationMessage).to.not.equal('');

				emailInput
					.shouldHaveValidationError(input.validationMessage)
					.shouldBeMarkedInvalid();
			});
		});
	});

	it('shows a custom typeMismatch message for an email field when configured', () => {
		createPublishedLiveFormPage(
			'validation-custom-email-form',
			'Validation Custom Email Form',
			[
				withValidationMessages(
					getInputEmailNode(CUSTOM_EMAIL_FIELD),
					{msgTypeMismatch: CUSTOM_TYPE_MISMATCH_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const emailInput = form.getEmailInput(CUSTOM_EMAIL_FIELD.name);

			emailInput.type('not-an-email');
			form.submit();

			emailInput
				.shouldHaveValidationError(CUSTOM_TYPE_MISMATCH_MESSAGE)
				.shouldBeMarkedInvalid();

			emailInput
				.type('valid@example.com')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});
});
