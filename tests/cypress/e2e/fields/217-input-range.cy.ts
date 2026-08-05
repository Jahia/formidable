import {
	getInputRangeNode,
	getInputTextNode,
	INPUT_RANGE_COMPLETE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

/**
 * fmdb:inputRange submits nothing until the visitor moves the slider: the visible
 * control is unnamed and mirrors its value into a hidden named input. An untouched
 * slider therefore stays distinguishable from an answered one (no pre-answered
 * bias) and 'required' has a real meaning for this control.
 */
describe('Form fields - 217 Input range', () => {
	useFormidableSite();

	it('stays unanswered until interaction and enforces required', () => {
		createPublishedLiveFormPage(
			'range-required-form',
			'Range Required Form',
			[getInputRangeNode(INPUT_RANGE_COMPLETE)]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const range = form.getRangeInput(INPUT_RANGE_COMPLETE.name!);

			range
				.shouldBeVisible()
				.shouldBeRangeInput()
				.shouldHaveMin(0)
				.shouldHaveMax(10)
				.shouldHaveStep(1)
				.shouldHaveEndLabels(INPUT_RANGE_COMPLETE.minLabel!, INPUT_RANGE_COMPLETE.maxLabel!)
				.shouldHaveDatalist()
				.shouldHaveDatalistOptions(INPUT_RANGE_COMPLETE.list!)
				.shouldHaveHelpText(INPUT_RANGE_COMPLETE.helpText!)
				.shouldBeUnanswered();

			// The label shows the required indicator even though the slider itself
			// carries no required attribute (HTML required does not apply to range).
			range.getLabel().find('.fmdb-required-indicator').should('exist');

			// The thumb rests at the midpoint while unanswered, like a valueless native range.
			range.getInput().should('have.value', '5');

			// Also the hydration gate for the interactions below.
			range.waitUntilRequiredArmed();

			// An untouched required slider must block submission with an inline error.
			form.submit();
			range.getValidationError().should('be.visible');
			form.getMessage().should('not.exist');

			range.setValue('7');
			range.shouldBeAnswered('7');
			range.shouldBeValid();
			// Answering clears the inline error immediately, without another submit.
			range.getValidationError().should('not.exist');

			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('starts answered when a default value is configured', () => {
		createPublishedLiveFormPage(
			'range-default-form',
			'Range Default Form',
			[getInputRangeNode({
				name: 'satisfaction',
				title: 'Satisfaction',
				minValue: 0,
				maxValue: 10,
				step: 1,
				defaultValue: 5,
				required: true
			})]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const range = form.getRangeInput('satisfaction');

			// defaultValue is an explicit editorial choice to start answered:
			// the hidden mirror carries it from the first render.
			range.shouldBeAnswered('5');
			range.getInput().should('have.value', '5');

			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('acts as a numeric conditional-logic source, unanswered until interaction', () => {
		const rule = JSON.stringify({
			logicId: 'rt-range',
			sourceFieldName: 'satisfaction',
			sourceFieldType: 'fmdb:inputRange',
			valueKind: 'number',
			operator: 'gt',
			value: '5'
		});

		createPublishedLiveFormPage(`range-logic-${Date.now()}`, 'Range logic form', [
			getInputRangeNode({
				name: 'satisfaction',
				title: 'satisfaction',
				minValue: 0,
				maxValue: 10,
				step: 1,
				required: true
			}),
			{
				...getInputTextNode({name: 'details', title: 'details'}),
				properties: [
					{name: 'jcr:title', value: 'details', language: 'en'},
					{name: 'logics', values: [rule]}
				]
			}
		]).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const range = form.getRangeInput('satisfaction');

			// The dependent field starts hidden: an untouched slider exposes no value,
			// so the numeric rule fails safe. Waiting for the attribute also guarantees
			// the logic listeners are hydrated.
			form.get().find('[data-fmdb-node-name="details"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('not.be.visible');
			range.waitUntilRequiredArmed();

			// 8 > 5 satisfies the rule; the island re-dispatches a change event after
			// committing the hidden mirror so rules evaluate against the fresh value.
			range.setValue('8');
			form.get().find('[data-fmdb-node-name="details"]').should('be.visible');

			range.setValue('3');
			form.get().find('[data-fmdb-node-name="details"]').should('not.be.visible');
		});
	});
});
