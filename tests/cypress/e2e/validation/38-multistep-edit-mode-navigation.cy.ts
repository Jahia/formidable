import {
	createPublishedLiveFormPage,
	getInputTextNode,
	getStepNode,
	visitEditForm,
	visitLiveForm
} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * Step navigation is free while authoring: a contributor working on step 2 must reach it
 * without first answering the required fields of step 1, and jumps straight to any step
 * by clicking its indicator. For a visitor nothing changes — the crossing validation
 * still blocks (covered by spec 34) and the indicators stay a passive progress trail.
 */
describe('Validation - 38 Step navigation while authoring', () => {
	useFormidableSite();

	const REQUIRED_STEP_ONE_FIELD = {
		name: 'editModeEmployeeCode',
		title: 'Employee code',
		required: true
	};

	const createStepFormPage = (suffix: string) => createPublishedLiveFormPage(
		`edit-mode-steps-${suffix}`,
		'Edit Mode Steps Form',
		[
			getStepNode({
				name: 'identityStepEditMode',
				title: 'Identity',
				label: 'Identity',
				children: [getInputTextNode(REQUIRED_STEP_ONE_FIELD)]
			}),
			getStepNode({
				name: 'detailsStepEditMode',
				title: 'Details',
				label: 'Details',
				children: [getInputTextNode({name: 'editModeComment', title: 'Comment'})]
			})
		]
	);

	it('crosses steps with an empty required field and jumps by clicking an indicator', () => {
		createStepFormPage('nav').then(({pagePath}) => {
			const form = visitEditForm(pagePath);

			form.shouldHaveCurrentStep('Identity');

			// The required field of step 1 is left empty on purpose: in live this blocks.
			form.nextStep();
			form.shouldHaveCurrentStep('Details');

			// The indicators are real buttons here, so the contributor jumps back directly.
			form.getStepIndicators().first().should('match', 'button').click();
			form.shouldHaveCurrentStep('Identity');
		});
	});

	it('keeps the indicators passive for a visitor', () => {
		createStepFormPage('visitor').then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.getStepIndicators().should('have.length', 2).and('not.match', 'button');
			form.getStepIndicators().last().click();
			form.shouldHaveCurrentStep('Identity');
		});
	});
});
