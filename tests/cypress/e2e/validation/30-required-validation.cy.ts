import {
	createPublishedLiveFormPage,
	getCheckboxNode,
	getInputFileNode,
	getRadioNode,
	getSelectNode,
	visitLiveForm,
	withValidationMessages
} from '../../support/fixtures';
import {useFormidableSite} from './support';

const REQUIRED_SELECT = {
	name: 'preferredOffice',
	title: 'Preferred office',
	required: true,
	options: [
		{value: '', label: 'Choose an office', selected: true},
		{value: 'paris', label: 'Paris', selected: false},
		{value: 'lyon', label: 'Lyon', selected: false}
	]
};

const REQUIRED_CHECKBOX = {
	name: 'acceptTerms',
	title: 'Accept Terms',
	required: true,
	choices: [{value: 'accepted', label: 'I accept the terms and conditions', selected: false}]
};

const REQUIRED_CHECKBOX_GROUP = {
	name: 'preferredTopics',
	title: 'Preferred topics',
	required: true,
	choices: [
		{value: 'news', label: 'News', selected: false},
		{value: 'events', label: 'Events', selected: false}
	]
};

const REQUIRED_FILE = {
	name: 'requiredDocument',
	title: 'Required document',
	required: true
};

const REQUIRED_RADIO = {
	name: 'preferredContactMethod',
	title: 'Preferred contact method',
	required: true,
	choices: [
		{value: 'email', label: 'Email', selected: false},
		{value: 'phone', label: 'Phone', selected: false}
	]
};

const REQUIRED_SELECT_MESSAGE = 'Pick an office before submitting.';
const CHECKBOX_MESSAGE = 'You must accept the terms and conditions to continue.';
const CHECKBOX_GROUP_MESSAGE = 'Select at least one preferred topic before continuing.';
const FILE_MESSAGE = 'Please attach a document before submitting.';
const RADIO_MESSAGE = 'Choose a preferred contact method before continuing.';

describe('Validation - 30 Required validation', () => {
	useFormidableSite();

	it('shows a custom required message for a select field and clears it after selection', () => {
		createPublishedLiveFormPage(
			'validation-required-select-form',
			'Validation Required Select Form',
			[
				withValidationMessages(
					getSelectNode(REQUIRED_SELECT),
					{msgValueMissing: REQUIRED_SELECT_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const select = form.getSelectInput(REQUIRED_SELECT.name);

			form.submit();

			select
				.shouldBeInvalid()
				.shouldBeMarkedInvalid()
				.shouldHaveValidationError(REQUIRED_SELECT_MESSAGE)
				.shouldBeFocused();

			select
				.select('Paris')
				.shouldBeValid()
				.shouldNotBeMarkedInvalid()
				.shouldNotHaveValidationError()
				.shouldHaveSelectedOption('Paris');
		});
	});

	it('shows a custom required message for a checkbox and clears it after checking', () => {
		createPublishedLiveFormPage(
			'validation-required-checkbox-form',
			'Validation Required Checkbox Form',
			[
				withValidationMessages(
					getCheckboxNode(REQUIRED_CHECKBOX),
					{msgValueMissing: CHECKBOX_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const checkbox = form.getCheckbox(REQUIRED_CHECKBOX.name);

			form.submit();

			checkbox
				.shouldHaveValidationError(CHECKBOX_MESSAGE)
				.shouldBeMarkedInvalid();

			checkbox
				.check()
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});

	it('shows a custom required message for a checkbox group and clears it after checking one option', () => {
		createPublishedLiveFormPage(
			'validation-required-checkbox-group-form',
			'Validation Required Checkbox Group Form',
			[
				withValidationMessages(
					getCheckboxNode(REQUIRED_CHECKBOX_GROUP),
					{msgValueMissing: CHECKBOX_GROUP_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const checkbox = form.getCheckbox(REQUIRED_CHECKBOX_GROUP.name);
			const checkboxGroup = form.getCheckboxGroup(REQUIRED_CHECKBOX_GROUP.name);

			form.submit();

			checkbox
				.shouldHaveValidationError(CHECKBOX_GROUP_MESSAGE)
				.shouldBeMarkedInvalid();

			checkboxGroup
				.getCheckbox('News')
				.check()
				.shouldBeChecked();

			checkbox
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});

	it('shows a custom required message for a file input when no file is selected', () => {
		createPublishedLiveFormPage(
			'validation-required-file-form',
			'Validation Required File Form',
			[
				withValidationMessages(
					getInputFileNode(REQUIRED_FILE),
					{msgValueMissing: FILE_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fileInput = form.getFileInput(REQUIRED_FILE.name);

			form.submit();

			fileInput
				.shouldHaveValidationError(FILE_MESSAGE)
				.shouldBeMarkedInvalid();
		});
	});

	it('shows a custom required message for a radio group and clears it after selection', () => {
		createPublishedLiveFormPage(
			'validation-required-radio-form',
			'Validation Required Radio Form',
			[
				withValidationMessages(
					getRadioNode(REQUIRED_RADIO),
					{msgValueMissing: RADIO_MESSAGE}
				)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const radio = form.getRadio(REQUIRED_RADIO.name);
			const radioGroup = form.getRadioGroup(REQUIRED_RADIO.name);

			form.submit();

			radio
				.shouldHaveValidationError(RADIO_MESSAGE)
				.shouldBeMarkedInvalid()
				.shouldBeFocused();

			radioGroup
				.select('Email')
				.shouldHaveSelected('Email');

			radio
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});
});
