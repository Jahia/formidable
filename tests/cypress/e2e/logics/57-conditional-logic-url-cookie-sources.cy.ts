import {ConditionalLogicEditor} from '../../page-object';
import {
	createConditionalLogicForm,
	getConditionalLogicNode,
	getInputTextNode,
	parseStoredLogicRule
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * Sources outside the form: a URL query parameter and a cookie. Both are providers, so
 * they carry a single config key instead of any source-field binding, share the provider
 * operator set, and are evaluated in the browser only.
 */
describe('Form logic - 57 URL parameter and cookie sources', () => {
	useFormidableSite();

	const wrapperOf = (fieldName: string) => cy.get(`[data-fmdb-node-name="${fieldName}"]`);

	// Hydration has run once the attribute is present: asserting before that would read the
	// server-rendered state, where every field carrying a rule is still hidden.
	const wrapperShouldBeHidden = (fieldName: string, hidden: boolean) =>
		wrapperOf(fieldName).should('have.attr', 'data-fmdb-logic-hidden', hidden ? 'true' : 'false');

	it('stores a URL parameter rule with no source-field binding', () => {
		createConditionalLogicForm(`${Date.now()}-urlparam`).then(({targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSourceType(0, 'URL parameter');

			// A provider rule has no source dropdown: the parameter name is typed in.
			logicField.ruleShouldHaveDropdownCount(0, 2);
			logicField.openProviderOperatorDropdown(0);
			logicField.menuShouldHaveItems(['equals', 'does not equal', 'contains', 'is defined', 'is not defined']);
			logicField.closeMenu();

			// 'is defined' needs no value, so the only text input is the parameter name.
			logicField.selectProviderOperator(0, 'is defined');
			logicField.ruleShouldHaveTextInputCount(0, 1);
			logicField.typeTextValue(0, 'promo');
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				return rawLogics.length === 1;
			}));

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				const storedRule = parseStoredLogicRule(rawLogics[0]);

				expect(storedRule.sourceType).to.equal('urlParam');
				expect(storedRule.param).to.equal('promo');
				expect(storedRule.operator).to.equal('exists');
				expect(storedRule.value).to.equal(undefined);

				// None of the field-source metadata, and no weakref: the rule references
				// nothing in the repository.
				expect(storedRule.sourceFieldName).to.equal(undefined);
				expect(storedRule.sourceNodeId).to.equal(undefined);
				expect(storedRule.sourceFieldKey).to.equal(undefined);
				expect(storedRule.variable).to.equal(undefined);
				expect(node.descendant?.children?.nodes ?? []).to.have.length(0);
			});
		});
	});

	it('shows the field only when the URL parameter matches', () => {
		const rule = JSON.stringify({
			logicId: 'rt-urlparam',
			sourceType: 'urlParam',
			param: 'promo',
			operator: 'equals',
			value: 'spring'
		});

		createPublishedLiveFormPage(`urlparam-live-${Date.now()}`, 'URL parameter live form', [
			getInputTextNode({name: 'fullname', title: 'fullname'}),
			{
				...getInputTextNode({name: 'promocode', title: 'promocode'}),
				properties: [
					{name: 'jcr:title', value: 'promocode', language: 'en'},
					{name: 'logics', values: [rule]}
				]
			}
		]).then(({livePath}) => {
			visitLiveForm(livePath, 'en', 'promo=spring');
			wrapperShouldBeHidden('promocode', false);

			// A different value, then no parameter at all: both leave the field hidden.
			visitLiveForm(livePath, 'en', 'promo=winter');
			wrapperShouldBeHidden('promocode', true);

			visitLiveForm(livePath);
			wrapperShouldBeHidden('promocode', true);
		});
	});

	it('shows the field only while the cookie is set', () => {
		const rule = JSON.stringify({
			logicId: 'rt-cookie',
			sourceType: 'cookie',
			cookie: 'fmdb-marketing',
			operator: 'exists'
		});

		createPublishedLiveFormPage(`cookie-live-${Date.now()}`, 'Cookie live form', [
			getInputTextNode({name: 'fullname', title: 'fullname'}),
			{
				...getInputTextNode({name: 'preferences', title: 'preferences'}),
				properties: [
					{name: 'jcr:title', value: 'preferences', language: 'en'},
					{name: 'logics', values: [rule]}
				]
			}
		]).then(({livePath}) => {
			cy.setCookie('fmdb-marketing', 'yes');
			visitLiveForm(livePath);
			wrapperShouldBeHidden('preferences', false);

			cy.clearCookie('fmdb-marketing');
			visitLiveForm(livePath);
			wrapperShouldBeHidden('preferences', true);
		});
	});
});
