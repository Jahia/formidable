import {ConditionalLogicEditor} from '../../page-object';
import {
	getInputNumberNode,
	getInputTextNode,
	INPUT_NUMBER_COMPLETE
} from '../../support/fixtures';
import {createFormNode, createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

describe('Form fields - 216 Input number', () => {
	useFormidableSite();

	it('renders numeric constraints and enforces native validation on submit', () => {
		createPublishedLiveFormPage(
			'number-validation-form',
			'Number Validation Form',
			[getInputNumberNode(INPUT_NUMBER_COMPLETE)]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const numberInput = form.getNumberInput(INPUT_NUMBER_COMPLETE.name!);

			numberInput
				.shouldBeVisible()
				.shouldBeNumberInput()
				.shouldBeRequired()
				.shouldHavePlaceholder(INPUT_NUMBER_COMPLETE.placeholder!)
				.shouldHaveHelpText(INPUT_NUMBER_COMPLETE.helpText!)
				.shouldHaveValue('5')
				.shouldHaveMin(1)
				.shouldHaveMax(10)
				.shouldHaveStep(0.5)
				.shouldHaveDatalist()
				.shouldHaveDatalistOptions(INPUT_NUMBER_COMPLETE.list!);

			// An empty required number must block submission before the submit handler runs.
			numberInput.clear();
			form.submit();
			numberInput
				.shouldBeInvalid()
				.shouldHaveValidityState('valueMissing', true);
			form.getMessage().should('not.exist');

			// A value above the configured maximum must fail native range validation.
			numberInput.type('42');
			numberInput
				.shouldBeInvalid()
				.shouldHaveValidityState('rangeOverflow', true);
			form.submit();
			form.getMessage().should('not.exist');

			// A value off the 0.5 grid (counted from minValue 1) must fail step validation.
			numberInput.type('5.3');
			numberInput
				.shouldBeInvalid()
				.shouldHaveValidityState('stepMismatch', true);
			form.submit();
			form.getMessage().should('not.exist');

			// A value on the grid and within bounds should submit successfully.
			numberInput.type('7.5');
			numberInput.shouldBeValid();
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('acts as a numeric conditional-logic source out of the box', () => {
		// The gap called out by issue #171: a plain "enter a number" question is the
		// most basic numeric source of all — fmdbmix:numberField in the CND makes it one.
		const formName = `number-logic-${Date.now()}`;
		createFormNode(formName, formName, [
			getInputNumberNode({name: 'score', title: 'score'}),
			getInputTextNode({name: 'nickname', title: 'nickname'})
		]).then(() => {
			const editor = ConditionalLogicEditor.visit(`${CONTENT_PATH}/${formName}/fields/nickname`);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.openSourceDropdown(0);
			logicField.menuShouldHaveItems(['score']);
			logicField.selectMenuItem('score');

			logicField.openOperatorDropdown(0);
			logicField.menuShouldHaveItems(['equals', 'is less than', 'is greater than', 'is between']);
			logicField.closeMenu();

			editor.cancelAndDiscard();
		});
	});
});
