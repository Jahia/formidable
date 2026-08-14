import {getNodeByPath, publishAndWaitJobEnding} from '@jahia/cypress';
import {getInputTextNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A stored rule is authored data: it can name a provider this module version does not
 * ship (a form authored against a newer engine) or carry no reference at all.
 *
 * The two cases part ways at save time: an unknown source type round-trips untouched
 * and must fail closed at runtime — target field hidden, wrapper flagged with
 * `data-fmdb-logic-unresolved` — while a shipped-provider rule left without a reference
 * is removed by the save-time cleanup, leaving the field ungated. Content that never
 * went through a save with the current engine (stored by an older version) can still
 * carry a reference-less rule, and the runtime must keep failing closed on it.
 */
describe('Form logic - 58 Unresolved rules fail closed', () => {
	useFormidableSite();

	const wrapperOf = (fieldName: string) => cy.get(`[data-fmdb-node-name="${fieldName}"]`);

	const createFormPage = (suffix: string, gatedProperties: Array<{name: string; value?: string; values?: string[]; language?: string}>) =>
		createPublishedLiveFormPage(
			`unresolved-live-${suffix}-${Date.now()}`,
			'Unresolved rule live form',
			[
				getInputTextNode({name: 'fullname', title: 'fullname'}),
				{
					...getInputTextNode({name: 'gated', title: 'gated'}),
					properties: gatedProperties
				}
			]
		);

	const withRule = (rule: object) => [
		{name: 'jcr:title', value: 'gated', language: 'en'},
		{name: 'logics', values: [JSON.stringify(rule)]}
	];

	it('keeps the field hidden and flags the wrapper for an unknown provider source type', () => {
		createFormPage('unknown-source', withRule({
			logicId: 'rt-unknown-source',
			sourceType: 'someFutureProvider',
			operator: 'exists'
		})).then(({livePath}) => {
			visitLiveForm(livePath);

			// The diagnostic attribute proves hydration evaluated the rule (and could not):
			// before hydration the field is hidden too, but without the flag.
			wrapperOf('gated')
				.should('have.attr', 'data-fmdb-logic-unresolved', 'source:someFutureProvider')
				.and('have.attr', 'data-fmdb-logic-hidden', 'true');
		});
	});

	it('removes a provider rule left without a reference at save, leaving the field ungated', () => {
		createFormPage('missing-ref', withRule({
			logicId: 'rt-missing-ref',
			sourceType: 'cookie',
			cookie: '',
			operator: 'exists'
		})).then(({formPath, livePath}) => {
			// The save-time cleanup is a background listener: wait until the stored
			// rule is gone, then republish so live reflects the cleaned state.
			cy.waitUntil(
				() => getNodeByPath(formPath + '/fields/gated', ['logics']).then(response => {
					const values = response?.data?.jcr?.nodeByPath?.properties?.[0]?.values;
					return !values || values.length === 0;
				}),
				{timeout: 15000, interval: 500, errorMsg: 'the targetless rule was never cleaned up'}
			);
			publishAndWaitJobEnding(formPath);

			const form = visitLiveForm(livePath);

			// The cleaned field carries no logic anymore: plainly visible, not flagged.
			form.getTextInput('gated').shouldBeVisible();
			wrapperOf('gated').should('not.have.attr', 'data-fmdb-logic-hidden');
			wrapperOf('gated').should('not.have.attr', 'data-fmdb-logic-unresolved');
		});
	});

	it('keeps the field hidden for a reference-less rule stored by an older version', () => {
		// The cleanup only runs on authoring saves: a rule written straight into live
		// reproduces legacy content, and the runtime must still fail closed on it.
		createFormPage('legacy-ref', [{name: 'jcr:title', value: 'gated', language: 'en'}])
			.then(({formPath, livePath}) => {
				cy.executeGroovy('groovy/setLiveLogics.groovy', {
					__FIELD_PATH__: `${formPath}/fields/gated`,
					__RULE_JSON__: '{"logicId":"rt-legacy-ref","sourceType":"cookie","cookie":"","operator":"exists"}'
				});

				// A cookie that would satisfy `exists` on a usable rule must not surface
				// the field: the rule reads no cookie at all.
				cy.setCookie('fmdb-any', 'yes');
				visitLiveForm(livePath);

				wrapperOf('gated')
					.should('have.attr', 'data-fmdb-logic-unresolved', 'ref:cookie')
					.and('have.attr', 'data-fmdb-logic-hidden', 'true');
			});
	});
});
