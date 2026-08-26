import {CONDITIONAL_LOGIC_FORM_ELEMENTS, getInputTextNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitEditForm, visitPreviewForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * Visibility rules describe the visitor experience, so they must not run in edit mode:
 * a field the rule hides would be invisible to the contributor, who could then neither
 * select nor configure it. Edit mode renders the whole form structure unconditionally;
 * live and preview keep hiding what the rules hide.
 */
describe('Form logic - 511 Rules never hide a field in edit mode', () => {
	useFormidableSite();

	const gatedRule = JSON.stringify({
		logicId: 'rt-edit-mode',
		sourceFieldName: 'role',
		sourceFieldType: 'fmdb:select',
		valueKind: 'choice',
		operator: 'in',
		value: 'admin'
	});

	const wrapperOf = (fieldName: string) => cy.get(`[data-fmdb-node-name="${fieldName}"]`);

	it('renders a gated field unhidden in edit mode while preview still hides it', () => {
		createPublishedLiveFormPage(
			`logic-edit-mode-${Date.now()}`,
			'Logic edit mode form',
			[
				...CONDITIONAL_LOGIC_FORM_ELEMENTS,
				{
					...getInputTextNode({name: 'gated', title: 'gated'}),
					properties: [
						{name: 'jcr:title', value: 'gated', language: 'en'},
						{name: 'logics', values: [gatedRule]}
					]
				}
			]
		).then(({pagePath, livePath}) => {
			// 'role' is left unselected, so the rule resolves to false everywhere:
			// only the rendering mode decides whether the field is hidden.
			visitEditForm(pagePath);
			// The rendering mode has to reach the island too: the hydrated form re-runs
			// the rules, so without this marker it would hide back what the server showed.
			cy.get('form.fmdb-form').should('have.attr', 'data-fmdb-edit-mode', 'true');
			wrapperOf('gated')
				.should('exist')
				.and('not.have.attr', 'data-fmdb-logic-hidden')
				.and('not.have.attr', 'aria-hidden');
			// Hiding also disables the controls it covers, so being visible is not enough:
			// the contributor must be able to type in the field.
			cy.get('input[name="gated"]').should('be.visible').and('not.be.disabled');

			visitPreviewForm(livePath);
			wrapperOf('gated').should('have.attr', 'data-fmdb-logic-hidden', 'true');
		});
	});
});
