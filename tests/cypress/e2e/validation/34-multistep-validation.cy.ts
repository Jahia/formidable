import {
	createPublishedLiveFormPage,
	getInputTextNode,
	getStepNode,
	visitLiveForm,
	withValidationMessages
} from '../../support/fixtures';
import {useFormidableSite} from './support';

const STEP_ONE_FIELD = {
	name: 'firstStepEmployeeCode',
	title: 'Employee code',
	required: true
};

const STEP_ONE_MESSAGE = 'Employee code is required before moving to the next step.';

const FINAL_STEP_FIELD = {
	name: 'stepTwoRequired',
	title: 'Required on last step',
	required: true
};

const FINAL_STEP_MESSAGE = 'This field is required before submitting.';

describe('Validation - 34 Multi-step validation', () => {
	useFormidableSite();

	it('blocks step navigation, focuses the first invalid field, and clears the error once valid', () => {
		createPublishedLiveFormPage(
			'validation-step-form',
			'Validation Step Form',
			[
				getStepNode({
					name: 'identityStepValidation',
					title: 'Identity',
					label: 'Identity',
					children: [
						withValidationMessages(
							getInputTextNode(STEP_ONE_FIELD),
							{msgValueMissing: STEP_ONE_MESSAGE}
						)
					]
				}),
				getStepNode({
					name: 'detailsStepValidation',
					title: 'Details',
					label: 'Details',
					children: [
						getInputTextNode({
							name: 'detailsCommentValidation',
							title: 'Details comment'
						})
					]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const firstStepInput = form.getTextInput(STEP_ONE_FIELD.name);

			form.shouldHaveCurrentStep('Identity');
			form.nextStep();

			form.shouldHaveCurrentStep('Identity');
			firstStepInput
				.shouldHaveValidationError(STEP_ONE_MESSAGE)
				.shouldBeMarkedInvalid()
				.shouldBeFocused();

			firstStepInput
				.type('AB-1234')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();

			form.nextStep();
			form.shouldHaveCurrentStep('Details');
		});
	});

	it('validates the final step on submit and blocks submission until the field is corrected', () => {
		createPublishedLiveFormPage(
			'validation-step-submit-form',
			'Validation Step Submit Form',
			[
				getStepNode({
					name: 'firstStep',
					title: 'Step One',
					label: 'Step One',
					children: [
						getInputTextNode({
							name: 'stepOneOptional',
							title: 'Optional field'
						})
					]
				}),
				getStepNode({
					name: 'secondStep',
					title: 'Step Two',
					label: 'Step Two',
					children: [
						withValidationMessages(
							getInputTextNode(FINAL_STEP_FIELD),
							{msgValueMissing: FINAL_STEP_MESSAGE}
						)
					]
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const lastStepInput = form.getTextInput(FINAL_STEP_FIELD.name);

			form.shouldHaveCurrentStep('Step One');
			form.nextStep();
			form.shouldHaveCurrentStep('Step Two');

			form.submit();

			lastStepInput
				.shouldHaveValidationError(FINAL_STEP_MESSAGE)
				.shouldBeMarkedInvalid()
				.shouldBeFocused();

			lastStepInput
				.type('Valid value')
				.shouldBeValid()
				.shouldNotHaveValidationError()
				.shouldNotBeMarkedInvalid();
		});
	});
});
