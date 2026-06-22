import type {JahiaNode} from '../../support/fixtures';
import {
	assertContentIntegrityClean
} from '../../support/contentIntegrity';
import {
	CHECKBOX_GROUP_COMPLETE,
	FORMIDABLE_TEST_SITE,
	getCheckboxNode,
	getFieldsetNode,
	getInputColorNode,
	getInputDateNode,
	getInputDatetimeLocalNode,
	getInputEmailNode,
	getInputFileNode,
	getInputTextNode,
	getLatestLiveFormSubmission,
	getRadioNode,
	getRichTextNode,
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
import {useFormidableSite} from '../support/useFormidableSite';

const SAVE_TO_JCR_ACTION: JahiaNode = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
};

const buildFullIntegrityForm = (): JahiaNode[] => [
	getStepNode({
		name: 'identityStep',
		title: 'Identity',
		label: 'Identity',
		intro: 'Provide the identity details for the requester.',
		children: [
			getRichTextNode({
				name: 'identityIntro',
				text: '<p><strong>Identity section</strong> for the full integrity test.</p>'
			}),
			getFieldsetNode({
				name: 'profileDetails',
				title: 'Profile details',
				children: [
					getInputTextNode({...INPUT_TEXT_COMPLETE, defaultValue: undefined}),
					getInputEmailNode({...INPUT_EMAIL_COMPLETE, defaultValue: undefined})
				]
			}),
			getCheckboxNode(CHECKBOX_GROUP_COMPLETE),
			getRadioNode(RADIO_GROUP),
			getSelectNode(SELECT_SINGLE)
		]
	}),
	getStepNode({
		name: 'detailsStep',
		title: 'Details',
		label: 'Details',
		intro: 'Provide the supporting details for the submission.',
		children: [
			getInputDateNode({...INPUT_DATE_COMPLETE, defaultValue: undefined}),
			getInputDatetimeLocalNode({...INPUT_DATETIME_LOCAL_COMPLETE, defaultValue: undefined}),
			getInputColorNode(INPUT_COLOR_COMPLETE),
			getTextareaNode({...TEXTAREA_COMPLETE, defaultValue: undefined}),
			getInputFileNode(INPUT_FILE_MULTIPLE)
		]
	})
];

describe('Content integrity - 60 Full form', () => {
	useFormidableSite();

	it('keeps a full multistep form and its saved submissions clean before and after submission', () => {
		const formName = `content-integrity-full-form-${Date.now()}`;
		const resultsRootPath = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}`;

		createPublishedLiveFormPage(
			formName,
			'Content integrity full form',
			buildFullIntegrityForm(),
			`${formName}-page`,
			'Content integrity full form page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({formPath, livePath}) => {
			assertContentIntegrityClean({startNode: formPath, workspace: 'EDIT'});

			const form = visitLiveForm(livePath);

			form.shouldHaveVisibleStepCount(2).shouldHaveCurrentStep('Identity');
			form.getFieldset('Profile details').getTextInput(INPUT_TEXT_COMPLETE.name!)
				.type('CI-1234')
				.shouldBeValid();
			form.getFieldset('Profile details').getEmailInput(INPUT_EMAIL_COMPLETE.name!)
				.type('integrity@example.com')
				.shouldBeValid();
			form.getCheckboxGroup(CHECKBOX_GROUP_COMPLETE.name!).checkByLabels(['Sports']);
			form.getRadioGroup(RADIO_GROUP.name!).select('Express').shouldHaveSelected('Express');
			form.getSelectInput(SELECT_SINGLE.name!).select('Support').shouldHaveSelectedOption('Support');
			form.nextStep();

			form.shouldHaveCurrentStep('Details');
			form.getDateInput(INPUT_DATE_COMPLETE.name!).setDate('2005-05-06').shouldBeValid();
			form.getDateTimeLocalInput(INPUT_DATETIME_LOCAL_COMPLETE.name!).setDateTime('2026-06-21T15:45').shouldBeValid();
			form.getColorInput(INPUT_COLOR_COMPLETE.name!).setColor('#225588').shouldBeValid();
			form.getTextarea(TEXTAREA_COMPLETE.name!)
				.type('A complete submission that exercises the full integrity scenario.')
				.shouldBeValid();
			form.getFileInput(INPUT_FILE_MULTIPLE.name!)
				.attachFile('cypress/fixtures/files/document.pdf')
				.shouldHaveSelectedFile('document.pdf');

			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');

			getLatestLiveFormSubmission(formName).then(({path}) => {
				expect(path.startsWith(`${resultsRootPath}/submissions`)).to.equal(true);
			});

			assertContentIntegrityClean({startNode: resultsRootPath, workspace: 'LIVE'});
		});
	});
});
