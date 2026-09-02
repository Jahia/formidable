import {createPublishedLiveFormPage, getCheckboxNode, getInputTextNode} from '../../support/fixtures';
import {visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

/**
 * A REQUIRED checkbox group hidden by conditional logic must not block the
 * submission. The group's client keeps a "select at least one" customValidity on
 * its (disabled) inputs while it is hidden — no change event will ever clear it
 * there — so the custom validation pass must mirror the browser's own rule and
 * skip every control barred from constraint validation (willValidate=false).
 * This is the exact field report of the 0.4.0 upgrade replay: Green + Yes could
 * not be sent because a hidden required Interests group vetoed the form.
 */
describe('Validation - 45 Required checkbox hidden by logic', () => {
	useFormidableSite();

	const showWhenRed = JSON.stringify({
		logicId: 'req-cb',
		sourceFieldName: 'colour',
		sourceFieldType: 'fmdb:inputText',
		valueKind: 'text',
		operator: 'equals',
		value: 'red'
	});

	it('submits while the group is hidden, blocks while it is visible and unchecked', () => {
		const interests = getCheckboxNode({
			name: 'interests',
			title: 'Interests',
			required: true,
			choices: [
				{value: 'music', label: 'Music'},
				{value: 'sport', label: 'Sport'}
			]
		});
		interests.properties = [...(interests.properties ?? []), {name: 'logics', values: [showWhenRed]}];

		createPublishedLiveFormPage(
			'required-hidden-checkbox-form',
			'Required Hidden Checkbox Form',
			[getInputTextNode({name: 'colour', title: 'Colour'}), interests]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// The group is hidden (colour is not red): the stale required state of its
			// disabled inputs must not veto the submission.
			form.getTextInput('colour').type('green');
			cy.get('[data-fmdb-node-name="interests"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true');
			form.submit();
			form.getSuccessMessage().should('be.visible');

			// Visible and unchecked: required applies again.
			const form2 = visitLiveForm(livePath);
			form2.getTextInput('colour').type('red');
			cy.get('[data-fmdb-node-name="interests"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'false');
			form2.submit();
			form2.getSuccessMessage().should('not.exist');
			cy.get('[data-fmdb-node-name="interests"] .fmdb-validation-error, [data-fmdb-node-name="interests"] ~ .fmdb-validation-error')
				.should('be.visible');
		});
	});
});
