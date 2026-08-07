import {getInputTextNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A stored rule is authored data: it can name a provider this module version does not
 * ship (a form authored against a newer engine) or carry no reference at all. Such a rule
 * cannot be evaluated, and it must fail closed — target field hidden, wrapper flagged with
 * `data-fmdb-logic-unresolved` — instead of vanishing and leaving the field visible while
 * the server counts it hidden.
 */
describe('Form logic - 58 Unresolved rules fail closed', () => {
	useFormidableSite();

	const wrapperOf = (fieldName: string) => cy.get(`[data-fmdb-node-name="${fieldName}"]`);

	const createFormPage = (suffix: string, rule: object) => createPublishedLiveFormPage(
		`unresolved-live-${suffix}-${Date.now()}`,
		'Unresolved rule live form',
		[
			getInputTextNode({name: 'fullname', title: 'fullname'}),
			{
				...getInputTextNode({name: 'gated', title: 'gated'}),
				properties: [
					{name: 'jcr:title', value: 'gated', language: 'en'},
					{name: 'logics', values: [JSON.stringify(rule)]}
				]
			}
		]
	);

	it('keeps the field hidden and flags the wrapper for an unknown provider source type', () => {
		createFormPage('unknown-source', {
			logicId: 'rt-unknown-source',
			sourceType: 'someFutureProvider',
			operator: 'exists'
		}).then(({livePath}) => {
			visitLiveForm(livePath);

			// The diagnostic attribute proves hydration evaluated the rule (and could not):
			// before hydration the field is hidden too, but without the flag.
			wrapperOf('gated')
				.should('have.attr', 'data-fmdb-logic-unresolved', 'source:someFutureProvider')
				.and('have.attr', 'data-fmdb-logic-hidden', 'true');
		});
	});

	it('keeps the field hidden and flags the wrapper for a provider rule without a reference', () => {
		createFormPage('missing-ref', {
			logicId: 'rt-missing-ref',
			sourceType: 'cookie',
			cookie: '',
			operator: 'exists'
		}).then(({livePath}) => {
			// The rule reads no cookie, so even one that would satisfy `exists` on a usable
			// rule must not surface the field.
			cy.setCookie('fmdb-any', 'yes');
			visitLiveForm(livePath);

			wrapperOf('gated')
				.should('have.attr', 'data-fmdb-logic-unresolved', 'ref:cookie')
				.and('have.attr', 'data-fmdb-logic-hidden', 'true');
		});
	});
});
