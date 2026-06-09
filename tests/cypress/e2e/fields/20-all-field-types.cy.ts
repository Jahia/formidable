import {
	CHECKBOX_GROUP_COMPLETE,
	FORMIDABLE_TEST_SITE,
	getCheckboxNode,
	getInputColorNode,
	getInputDateNode,
	getInputDatetimeLocalNode,
	getInputEmailNode,
	getInputFileNode,
	getInputTextNode,
	getRadioNode,
	getSelectNode,
	getStepNode,
	getTextareaNode,
	INPUT_COLOR_COMPLETE,
	INPUT_DATE_COMPLETE,
	INPUT_DATETIME_LOCAL_COMPLETE,
	INPUT_EMAIL_COMPLETE,
	INPUT_FILE_MULTIPLE,
	INPUT_TEXT_COMPLETE,
	RADIO_GROUP,
	SELECT_SINGLE,
	TEXTAREA_COMPLETE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 20 All field types', () => {
	useFormidableSite();

	it('submits a simple live form with all supported field types', () => {
		createPublishedLiveFormPage(
			'all-fields-simple-form',
			'All Fields Simple Form',
			[
				getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined}),
				getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined}),
				getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined}),
				getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined}),
				getInputColorNode(INPUT_COLOR_COMPLETE),
				getCheckboxNode(CHECKBOX_GROUP_COMPLETE),
				getRadioNode(RADIO_GROUP),
				getSelectNode(SELECT_SINGLE),
				getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}),
				getInputFileNode(INPUT_FILE_MULTIPLE)
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.getTextInput(INPUT_TEXT_COMPLETE.name!)
				.type('AB-9876')
				.shouldBeValid();
			form.getEmailInput(INPUT_EMAIL_COMPLETE.name!)
				.type('contact@example.com')
				.shouldBeValid();
			form.getDateInput(INPUT_DATE_COMPLETE.name!)
				.setDate('2001-02-03')
				.shouldBeValid();
			form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_COMPLETE.name!)
				.setDateTime('2026-06-12T09:30')
				.shouldBeValid();
			form.getColorInput(INPUT_COLOR_COMPLETE.name!)
				.setColor('#336699')
				.shouldBeValid();
			form.getCheckboxGroup(CHECKBOX_GROUP_COMPLETE.name!)
				.checkByLabels(['Sports']);
			form.getRadioGroup(RADIO_GROUP.name!)
				.select('Pickup')
				.shouldHaveSelected('Pickup');
			form.getSelectInput(SELECT_SINGLE.name!)
				.select('Support')
				.shouldHaveSelectedOption('Support');
			form.getTextarea(TEXTAREA_COMPLETE.name!)
				.type('A longer project summary that satisfies the field constraints.')
				.shouldBeValid();
			form.getFileInput(INPUT_FILE_MULTIPLE.name!)
				.attachFile('cypress/fixtures/files/sample.csv')
				.shouldHaveSelectedFile('sample.csv');

			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('submits a multistep live form with all supported field types distributed across steps', () => {
		createPublishedLiveFormPage(
			'all-fields-step-form',
			'All Fields Step Form',
			[
				getStepNode({
					name: 'identityStep',
					title: 'Identity',
					label: 'Identity',
					children: [
						getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined}),
						getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined}),
						getCheckboxNode(CHECKBOX_GROUP_COMPLETE),
						getRadioNode(RADIO_GROUP),
						getSelectNode(SELECT_SINGLE)
					]
				}),
				getStepNode({
					name: 'detailsStep',
					title: 'Details',
					label: 'Details',
					children: [
						getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined}),
						getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined}),
						getInputColorNode(INPUT_COLOR_COMPLETE),
						getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}),
						getInputFileNode(INPUT_FILE_MULTIPLE)
					]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.shouldHaveVisibleStepCount(2).shouldHaveCurrentStep('Identity');
			form.getTextInput(INPUT_TEXT_COMPLETE.name!).type('CD-4567');
			form.getEmailInput(INPUT_EMAIL_COMPLETE.name!).type('step@example.com');
			form.getCheckboxGroup(CHECKBOX_GROUP_COMPLETE.name!).checkByLabels(['Sports']);
			form.getRadioGroup(RADIO_GROUP.name!).select('Express');
			form.getSelectInput(SELECT_SINGLE.name!).select('Sales');
			form.nextStep();

			form.shouldHaveCurrentStep('Details');
			form.getDateInput(INPUT_DATE_COMPLETE.name!).setDate('2004-04-05');
			form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_COMPLETE.name!).setDateTime('2026-06-20T14:15');
			form.getColorInput(INPUT_COLOR_COMPLETE.name!).setColor('#663399');
			form.getTextarea(TEXTAREA_COMPLETE.name!).type('Second step details long enough for textarea validation.');
			form.getFileInput(INPUT_FILE_MULTIPLE.name!).attachFile('cypress/fixtures/files/document.pdf');

			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});
});
