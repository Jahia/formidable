import {ConditionalLogicEditor} from '../../page-object';
import {
	createFormContentConditionalLogicForm,
	getConditionalLogicNode,
	getRichTextNode,
	getSelectNode,
	parseStoredLogicRule
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * Content that is not a field (fmdbmix:formContent, the rich text) carries conditional display
 * rules like a field: fmdbmix:formContent extends fmdbmix:formLogicElement. It is a target only,
 * never a source, because it has no value.
 */
describe('Form logic - 513 Conditional display of form content', () => {
	useFormidableSite();

	it('offers the fields before a rich text as sources, and stores its rule', () => {
		createFormContentConditionalLogicForm(`${Date.now()}-editor`).then(({rolePath, targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.openSourceDropdown(0);
			// 'intro' is a rich text before the target: no value, so no source. 'nickname' stands after it.
			logicField.menuShouldHaveItems(['role']);
			logicField.menuShouldNotHaveItems(['intro', 'admin-help', 'nickname']);
			logicField.selectMenuItem('role');
			logicField.selectValue(0, 'admin');
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node => {
				const logicChildren = node.descendant?.children?.nodes ?? [];
				return logicChildren.length === 1 && Boolean(logicChildren[0].property?.refNode?.uuid);
			}));

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				expect(rawLogics).to.have.length(1);

				const storedRule = parseStoredLogicRule(rawLogics[0]);
				expect(storedRule.sourceFieldName).to.equal('role');
				expect(storedRule.operator).to.equal('in');

				const logicChild = node.descendant?.children?.nodes?.[0];
				expect(logicChild?.property?.refNode?.path).to.equal(rolePath);
			});
		});
	});

	it('shows a rich text only while its rule matches in the live form', () => {
		const adminRule = JSON.stringify({
			logicId: 'rt-content',
			sourceFieldName: 'role',
			sourceFieldType: 'fmdb:select',
			valueKind: 'choice',
			operator: 'in',
			value: 'admin'
		});

		createPublishedLiveFormPage(`content-live-${Date.now()}`, 'Form content live form', [
			getSelectNode({
				name: 'role',
				title: 'role',
				options: [
					{value: 'admin', label: 'admin', selected: false},
					{value: 'editor', label: 'editor', selected: false}
				]
			}),
			getRichTextNode({name: 'intro', text: '<p>intro</p>'}),
			{
				...getRichTextNode({name: 'admin-help', text: '<p>admin-help</p>'}),
				properties: [
					{name: 'text', value: '<p>admin-help</p>', language: 'en'},
					{name: 'logics', values: [adminRule]}
				]
			}
		]).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const wrapperOf = (name: string) => form.get().find(`[data-fmdb-node-name="${name}"]`);

			// Waiting for the data attribute also guarantees hydration has run.
			wrapperOf('admin-help')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('have.class', 'fmdb-logic-target')
				.and('not.be.visible');
			// A rich text without a rule is untouched.
			wrapperOf('intro').should('be.visible');
			wrapperOf('intro').should('not.have.class', 'fmdb-logic-target');

			form.getSelectInput('role').select('admin');
			wrapperOf('admin-help').should('have.attr', 'data-fmdb-logic-hidden', 'false');
			wrapperOf('admin-help').should('be.visible').and('contain', 'admin-help');

			form.getSelectInput('role').select('editor');
			wrapperOf('admin-help').should('have.attr', 'data-fmdb-logic-hidden', 'true');
			wrapperOf('admin-help').should('not.be.visible');
		});
	});
});
