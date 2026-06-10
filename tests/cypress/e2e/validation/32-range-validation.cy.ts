import {
	createPublishedLiveFormPage,
	getInputDateNode,
	getInputDatetimeLocalNode,
	visitLiveForm,
	withValidationMessages
} from '../../support/fixtures';
import {useFormidableSite} from './support';

const DATE_FIELD = {
	name: 'appointmentDateValidation',
	title: 'Appointment date',
	required: true,
	min: '2026-06-01T00:00:00.000',
	max: '2026-06-10T00:00:00.000',
	step: 2
};

const DATE_MESSAGES = {
	msgValueMissing: 'Pick an appointment date.',
	msgRangeUnderflow: 'The appointment date is too early.',
	msgRangeOverflow: 'The appointment date is too late.',
	msgStepMismatch: 'Appointments are available every two days only.'
};

const DATETIME_FIELD = {
	name: 'meetingDatetime',
	title: 'Meeting date and time',
	required: true,
	min: '2026-06-01T08:00:00.000',
	max: '2026-06-10T18:00:00.000'
};

const DATETIME_MESSAGES = {
	msgValueMissing: 'Pick a meeting date and time.',
	msgRangeUnderflow: 'The meeting date is too early.',
	msgRangeOverflow: 'The meeting date is too late.'
};

describe('Validation - 32 Range validation', () => {
	useFormidableSite();

	it('shows custom inline messages for required, min, max, and step date constraints', () => {
		createPublishedLiveFormPage(
			'validation-date-range-form',
			'Validation Date Range Form',
			[
				withValidationMessages(getInputDateNode(DATE_FIELD), DATE_MESSAGES)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const dateInput = form.getDateInput(DATE_FIELD.name);

			form.submit();
			dateInput.shouldHaveValidationError(DATE_MESSAGES.msgValueMissing);

			dateInput.setDate('2026-05-31');
			form.submit();
			dateInput.shouldHaveValidationError(DATE_MESSAGES.msgRangeUnderflow);

			dateInput.setDate('2026-06-12');
			form.submit();
			dateInput.shouldHaveValidationError(DATE_MESSAGES.msgRangeOverflow);

			dateInput.setDate('2026-06-02');
			form.submit();
			dateInput.shouldHaveValidationError(DATE_MESSAGES.msgStepMismatch);

			dateInput
				.setDate('2026-06-03')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});

	it('shows custom inline messages for required, min, and max datetime-local constraints', () => {
		createPublishedLiveFormPage(
			'validation-datetime-range-form',
			'Validation Datetime Range Form',
			[
				withValidationMessages(getInputDatetimeLocalNode(DATETIME_FIELD), DATETIME_MESSAGES)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const datetimeInput = form.getDateTimeLocalInput(DATETIME_FIELD.name);

			form.submit();
			datetimeInput.shouldHaveValidationError(DATETIME_MESSAGES.msgValueMissing);

			datetimeInput.setDateTime('2026-05-31T07:00');
			form.submit();
			datetimeInput.shouldHaveValidationError(DATETIME_MESSAGES.msgRangeUnderflow);

			datetimeInput.setDateTime('2026-06-11T19:00');
			form.submit();
			datetimeInput.shouldHaveValidationError(DATETIME_MESSAGES.msgRangeOverflow);

			datetimeInput
				.setDateTime('2026-06-05T10:00')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});
});
