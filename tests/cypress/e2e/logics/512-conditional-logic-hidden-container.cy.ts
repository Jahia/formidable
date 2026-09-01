import {createPublishedLiveFormPage, getInputTextNode, getStepNode} from '../../support/fixtures';
import {visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A field with a satisfied rule of its own must NOT come back to life inside a
 * logic-hidden container. The visibility pass walks wrappers in document order, so
 * the container's verdict is already on the DOM when the field is processed: its
 * controls must stay disabled, or FormData posts the value of a field the server
 * can prove hidden (the field inherits the step's verdict) and the whole submission
 * is rejected with FMDB-013 — a legitimate visitor losing a filled form.
 */
describe('Form logic - 512 Field inside a logic-hidden container', () => {
	useFormidableSite();

	const stepRule = JSON.stringify({
		logicId: 'hc-step',
		sourceFieldName: 'gate',
		sourceFieldType: 'fmdb:inputText',
		valueKind: 'text',
		operator: 'equals',
		value: 'open'
	});

	const fieldRule = JSON.stringify({
		logicId: 'hc-field',
		sourceFieldName: 'country',
		sourceFieldType: 'fmdb:inputText',
		valueKind: 'text',
		operator: 'equals',
		value: 'France'
	});

	it('keeps the field disabled when its container hides, and the submission passes', () => {
		const detailsField = getInputTextNode({name: 'extraDetails', title: 'Extra details'});
		detailsField.properties = [...(detailsField.properties ?? []), {name: 'logics', values: [fieldRule]}];

		const conditionalStep = getStepNode({
			name: 'conditionalStep',
			title: 'Conditional step',
			label: 'Conditional step',
			children: [detailsField]
		});
		conditionalStep.properties = [...(conditionalStep.properties ?? []), {name: 'logics', values: [stepRule]}];

		createPublishedLiveFormPage(
			'hidden-container-form',
			'Hidden Container Form',
			[
				getStepNode({
					name: 'gateStep',
					title: 'Gate step',
					label: 'Gate step',
					children: [
						getInputTextNode({name: 'gate', title: 'Gate'}),
						getInputTextNode({name: 'country', title: 'Country'})
					]
				}),
				conditionalStep
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// Both rules satisfied: reach the conditional step and fill its field.
			form.getTextInput('gate').type('open');
			form.getTextInput('country').type('France');
			form.nextStep();
			form.getTextInput('extraDetails').shouldBeVisible().type('some details');

			// Back on step 1, close the gate: the step hides, and the field's own
			// still-satisfied rule must not re-enable its control inside it.
			form.previousStep();
			form.getTextInput('gate').clear().type('closed');

			cy.get('[data-fmdb-node-name="conditionalStep"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true');
			form.getTextInput('extraDetails').shouldBeDisabled();

			// The value is not posted, so the server's coherence check has nothing to
			// reject: the honest flow ends in a success, not an FMDB-013 refusal.
			form.submit();
			form.getSuccessMessage().should('be.visible');
		});
	});
});
