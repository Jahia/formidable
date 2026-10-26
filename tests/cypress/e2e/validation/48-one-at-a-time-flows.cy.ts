import {
	createPublishedLiveFormPage,
	getBlockedWordsFieldActionNode,
	getCheckboxNode,
	getFieldsetNode,
	getInputTextNode,
	getSaveToJcrActionNode,
	getStepNode,
	visitLiveForm,
	withFieldActions,
	withValidationMessages
} from '../../support/fixtures';
import type {JahiaNode, NodeProperty} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * The behaviours a Classic form already has, checked in One-at-a-time on a form with steps and on a form
 * without: the constraints and the field actions of the screen hold the visitor on it, the conditional logic
 * takes hidden fields and containers out of the path and out of the submission, the last screen validates
 * on Submit, and a new form starts again at its first screen. The Classic counterparts: specs 34, 44, 45,
 * 512 and 73.
 */
const ONE_AT_A_TIME: NodeProperty = {name: 'formExperience', value: 'oneAtATime'};

/** The fields of each step; the form without steps gets the same fields in one list. */
interface Layout {
	label: string;
	suffix: string;
	build: (groups: JahiaNode[][]) => JahiaNode[];
}

const LAYOUTS: Layout[] = [
	{label: 'without steps', suffix: 'flat', build: groups => groups.flat()},
	{
		label: 'with steps',
		suffix: 'steps',
		build: groups => groups.map((children, index) => getStepNode({
			name: `step${index + 1}`,
			title: `Step ${index + 1}`,
			label: `Step ${index + 1}`,
			children
		}))
	}
];

const withLogic = (node: JahiaNode, rule: object): JahiaNode => ({
	...node,
	properties: [...(node.properties ?? []), {name: 'logics', values: [JSON.stringify(rule)]}]
});

const textEquals = (logicId: string, sourceFieldName: string, value: string) => ({
	logicId,
	sourceFieldName,
	sourceFieldType: 'fmdb:inputText',
	valueKind: 'text',
	operator: 'equals',
	value
});

describe('Validation - 48 One-at-a-time flows', () => {
	useFormidableSite();

	LAYOUTS.forEach(({label, suffix, build}) => {
		const create = (name: string, groups: JahiaNode[][], properties: NodeProperty[] = []) =>
			createPublishedLiveFormPage(`${name}-${suffix}`, `${name} ${suffix}`, build(groups), undefined, undefined, {
				properties: [ONE_AT_A_TIME, ...properties],
				actions: [getSaveToJcrActionNode()]
			});

		it(`${label}: a required field keeps the visitor on its screen until it holds`, () => {
			const message = 'The employee code is required.';
			create('oaat-required', [
				[withValidationMessages(getInputTextNode({name: 'employeeCode', title: 'Employee code', required: true}), {msgValueMissing: message})],
				[getInputTextNode({name: 'comment', title: 'Comment'})]
			]).then(({livePath}) => {
				const form = visitLiveForm(livePath);
				const code = form.getTextInput('employeeCode');

				form.nextStep();
				form.shouldShowScreenOf('employeeCode');
				code.shouldHaveValidationError(message).shouldBeMarkedInvalid().shouldBeFocused();

				code.type('AB-1234').shouldNotHaveValidationError().shouldNotBeMarkedInvalid();
				form.nextStep();
				form.shouldShowScreenOf('comment');
				form.getTextInput('comment').shouldBeFocused();
			});
		});

		it(`${label}: Submit validates the last screen`, () => {
			const message = 'This field is required before submitting.';
			create('oaat-last-screen', [
				[getInputTextNode({name: 'optionalFirst', title: 'Optional field'})],
				[withValidationMessages(getInputTextNode({name: 'requiredLast', title: 'Required on last screen', required: true}), {msgValueMissing: message})]
			]).then(({livePath}) => {
				const form = visitLiveForm(livePath);
				form.getTextInput('optionalFirst').type('{enter}');
				form.shouldShowScreenOf('requiredLast');

				form.submit();
				form.getTextInput('requiredLast').shouldHaveValidationError(message).shouldBeFocused();
				form.getSuccessMessage().should('not.exist');

				form.getTextInput('requiredLast').type('Done');
				form.submit();
				form.getSuccessMessage().should('be.visible');
			});
		});

		it(`${label}: Submit brings back a required field that a later answer showed on a passed screen`, () => {
			const message = 'The reference is required.';
			const reference = withLogic(withValidationMessages(getInputTextNode({name: 'reference', title: 'Reference', required: true}), {msgValueMissing: message}),
				textEquals('oaat-back-ref', 'hasReference', 'yes'));
			create('oaat-shown-behind', [
				[getInputTextNode({name: 'firstAnswer', title: 'First answer'}), reference],
				[getInputTextNode({name: 'hasReference', title: 'Do you have a reference?'})]
			]).then(({livePath}) => {
				// The reference is hidden when the visitor passes its place, and shown by the last answer
				const form = visitLiveForm(livePath);
				form.getTextInput('firstAnswer').type('One{enter}');
				form.shouldShowScreenOf('hasReference');
				form.getTextInput('hasReference').type('yes');

				form.submit();
				form.shouldShowScreenOf('reference');
				form.getTextInput('reference').shouldHaveValidationError(message).shouldBeFocused();
				form.getSuccessMessage().should('not.exist');
			});
		});

		it(`${label}: a required group hidden by logic is skipped and does not block the submission`, () => {
			const interests = withLogic(getCheckboxNode({
				name: 'interests',
				title: 'Interests',
				required: true,
				choices: [{value: 'music', label: 'Music'}, {value: 'sport', label: 'Sport'}]
			}), textEquals('oaat-req-cb', 'colour', 'red'));

			create('oaat-hidden-required', [
				[getInputTextNode({name: 'colour', title: 'Colour'})],
				[interests, getInputTextNode({name: 'closing', title: 'Closing remark'})]
			]).then(({livePath}) => {
				// Green: the group leaves the path, the next screen is the closing remark
				const form = visitLiveForm(livePath);
				form.getTextInput('colour').type('green{enter}');
				form.shouldShowScreenOf('closing');
				form.submit();
				form.getSuccessMessage().should('be.visible');

				// Red: the group joins the path, and required applies to it
				const again = visitLiveForm(livePath);
				again.getTextInput('colour').type('red{enter}');
				again.shouldShowScreenOf('interests');
				again.nextStep();
				again.shouldShowScreenOf('interests');
				cy.get('[data-fmdb-node-name="interests"] .fmdb-validation-error, [data-fmdb-node-name="interests"] ~ .fmdb-validation-error')
					.should('be.visible');
			});
		});

		it(`${label}: a container hidden by logic takes its fields out of the path and of the submission`, () => {
			// The container is the second step, or a fieldset in the form without steps; its field has a
			// satisfied rule of its own, which must not bring it back (spec 512)
			const details = withLogic(getInputTextNode({name: 'extraDetails', title: 'Extra details'}),
				textEquals('oaat-hc-field', 'country', 'France'));
			const hiddenRule = textEquals('oaat-hc-container', 'gate', 'open');
			const groups = suffix === 'flat'
				? [[getInputTextNode({name: 'gate', title: 'Gate'}), getInputTextNode({name: 'country', title: 'Country'}),
					withLogic(getFieldsetNode({name: 'conditionalSet', title: 'Conditional set', children: [details]}), hiddenRule),
					getInputTextNode({name: 'closing', title: 'Closing remark'})]]
				: [[getInputTextNode({name: 'gate', title: 'Gate'}), getInputTextNode({name: 'country', title: 'Country'})],
					[details],
					[getInputTextNode({name: 'closing', title: 'Closing remark'})]];
			const formNodes = build(groups);
			if (suffix === 'steps') formNodes[1] = withLogic(formNodes[1], hiddenRule);

			createPublishedLiveFormPage(`oaat-hidden-container-${suffix}`, `oaat hidden container ${suffix}`, formNodes,
				undefined, undefined, {properties: [ONE_AT_A_TIME], actions: [getSaveToJcrActionNode()]}
			).then(({livePath}) => {
				const form = visitLiveForm(livePath);
				form.getTextInput('gate').type('closed{enter}');
				form.getTextInput('country').type('France{enter}');
				form.shouldShowScreenOf('closing');
				form.get().find('input[name="extraDetails"]').should('be.disabled');
				form.submit();
				form.getSuccessMessage().should('be.visible');
			});
		});

		it(`${label}: a field action refusal keeps the visitor on the screen until the value changes`, () => {
			create('oaat-field-action', [
				[withFieldActions(getInputTextNode({name: 'nickname', title: 'Nickname'}), [
					getBlockedWordsFieldActionNode({name: 'noSpam', words: ['spam'], rejectionMessage: '<b>${value}</b> is not welcome here.'})
				])],
				[getInputTextNode({name: 'closing', title: 'Closing remark'})]
			]).then(({livePath}) => {
				const form = visitLiveForm(livePath);
				form.getTextInput('nickname').type('spam');
				form.nextStep();
				form.shouldShowScreenOf('nickname');
				cy.get('[data-fmdb-node-name="nickname"] .fmdb-validation-error')
					.should('be.visible')
					.and('contain.html', '<b>spam</b> is not welcome here.');

				form.getTextInput('nickname').get().clear().type('Ada');
				form.nextStep();
				form.shouldShowScreenOf('closing');
			});
		});

		it(`${label}: a new form starts again at the first screen`, () => {
			create('oaat-new-form', [
				[getInputTextNode({name: 'firstAnswer', title: 'First answer'})],
				[getInputTextNode({name: 'secondAnswer', title: 'Second answer'})]
			], [{name: 'showNewFormBtn', value: 'true', type: 'BOOLEAN'}]).then(({livePath}) => {
				const form = visitLiveForm(livePath);
				form.getTextInput('firstAnswer').type('One{enter}');
				form.getTextInput('secondAnswer').type('Two');
				form.submit();
				form.getSuccessMessage().should('be.visible');

				form.getNewFormButton().click();
				form.shouldShowScreenOf('firstAnswer');
				form.getTextInput('firstAnswer').get().should('have.value', '');
				if (suffix === 'steps') form.shouldHaveCurrentStep('Step 1');
			});
		});
	});

	it('without steps: Reset shows next to Submit on the last screen, and starts again at the first one', () => {
		createPublishedLiveFormPage('oaat-reset', 'oaat reset', [
			getInputTextNode({name: 'firstAnswer', title: 'First answer'}),
			getInputTextNode({name: 'secondAnswer', title: 'Second answer'})
		], undefined, undefined, {properties: [ONE_AT_A_TIME, {name: 'showResetBtn', value: 'true', type: 'BOOLEAN'}]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.get().find('.fmdb-form-actions button[type="reset"]').should('not.exist');

			form.getTextInput('firstAnswer').type('One{enter}');
			form.shouldShowScreenOf('secondAnswer');
			form.getTextInput('secondAnswer').type('Two');
			form.getResetButton().get().should('be.visible');

			form.reset();
			form.shouldShowScreenOf('firstAnswer');
			form.getTextInput('firstAnswer').get().should('have.value', '');
		});
	});

	it('with steps and no steps navigation: the step title shows above each screen of its step', () => {
		createPublishedLiveFormPage('oaat-no-nav', 'oaat no nav', [
			getStepNode({
				name: 'identityStep',
				title: 'Identity',
				label: 'Identity',
				children: [getInputTextNode({name: 'firstName', title: 'First name'}), getInputTextNode({name: 'lastName', title: 'Last name'})]
			}),
			getStepNode({name: 'detailsStep', title: 'Details', label: 'Details', children: [getInputTextNode({name: 'comment', title: 'Comment'})]})
		], undefined, undefined, {properties: [ONE_AT_A_TIME, {name: 'showStepsNav', value: 'false', type: 'BOOLEAN'}]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.get().find('.fmdb-steps-nav').should('not.exist');

			form.shouldShowScreenOf('firstName');
			form.get().find('.fmdb-step-title:visible').should('have.length', 1).and('contain', 'Identity');
			form.nextStep();
			form.shouldShowScreenOf('lastName');
			form.get().find('.fmdb-step-title:visible').should('have.length', 1).and('contain', 'Identity');
			form.nextStep();
			form.shouldShowScreenOf('comment');
			form.get().find('.fmdb-step-title:visible').should('have.length', 1).and('contain', 'Details');
		});
	});
});
