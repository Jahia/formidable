import {ConditionalLogicEditor} from '../../page-object';
import {
	createTextSourcesConditionalLogicForm,
	getConditionalLogicNode,
	getInputTextNode,
	parseStoredLogicRule
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * Core text inputs (fmdb:inputText, fmdb:textarea, fmdb:inputEmail) carry
 * fmdbmix:textField and are therefore conditional-logic sources with the text
 * operators — the "show this field once that one is filled" use case.
 */
describe('Form logic - 55 Text field sources', () => {
	useFormidableSite();

	it('offers text inputs as sources with the text operators and stores valueKind', () => {
		createTextSourcesConditionalLogicForm(`${Date.now()}-editor`).then(({fullnamePath, targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.openSourceDropdown(0);
			logicField.menuShouldHaveItems(['fullname', 'notes', 'email']);
			logicField.selectMenuItem('fullname');

			logicField.openOperatorDropdown(0);
			logicField.menuShouldHaveItems(['is filled', 'is empty', 'equals', 'contains']);
			logicField.selectMenuItem('is filled');

			// Source type + source + operator only: emptiness operators need no value widget.
			logicField.ruleShouldHaveDropdownCount(0, 3);
			logicField.ruleShouldHaveTextInputCount(0, 0);

			logicField.selectOperator(0, 'contains');
			logicField.ruleShouldHaveTextInputCount(0, 1);
			logicField.typeTextValue(0, 'vip');
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node => {
				const logicChildren = node.descendant?.children?.nodes ?? [];
				return logicChildren.length === 1 && Boolean(logicChildren[0].property?.refNode?.uuid);
			}));

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				expect(rawLogics).to.have.length(1);

				const storedRule = parseStoredLogicRule(rawLogics[0]);
				expect(storedRule.sourceFieldName).to.equal('fullname');
				expect(storedRule.sourceFieldType).to.equal('fmdb:inputText');
				expect(storedRule.valueKind).to.equal('text');
				expect(storedRule.operator).to.equal('contains');
				expect(storedRule.value).to.equal('vip');

				const logicChild = node.descendant?.children?.nodes?.[0];
				expect(logicChild?.property?.refNode?.path).to.equal(fullnamePath);
			});
		});
	});

	it('offers textarea and email sources and stores emptiness operators without a value', () => {
		createTextSourcesConditionalLogicForm(`${Date.now()}-empty`).then(({notesPath, targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSource(0, 'email');
			logicField.openOperatorDropdown(0);
			logicField.menuShouldHaveItems(['is filled', 'is empty', 'equals', 'contains']);
			logicField.closeMenu();

			logicField.selectSource(0, 'notes');
			logicField.selectOperator(0, 'is empty');
			logicField.ruleShouldHaveDropdownCount(0, 3);
			logicField.ruleShouldHaveTextInputCount(0, 0);
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node => {
				const logicChildren = node.descendant?.children?.nodes ?? [];
				return logicChildren.length === 1 && Boolean(logicChildren[0].property?.refNode?.uuid);
			}));

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				expect(rawLogics).to.have.length(1);

				const storedRule = parseStoredLogicRule(rawLogics[0]);
				expect(storedRule.sourceFieldName).to.equal('notes');
				expect(storedRule.sourceFieldType).to.equal('fmdb:textarea');
				expect(storedRule.valueKind).to.equal('text');
				expect(storedRule.operator).to.equal('isEmpty');
				expect(storedRule.value).to.equal(undefined);
				expect(storedRule.values ?? []).to.have.length(0);

				const logicChild = node.descendant?.children?.nodes?.[0];
				expect(logicChild?.property?.refNode?.path).to.equal(notesPath);
			});
		});
	});

	it('evaluates isNotEmpty and contains in the live form, treating whitespace as empty', () => {
		const filledRule = JSON.stringify({
			logicId: 'rt-filled',
			sourceFieldName: 'fullname',
			sourceFieldType: 'fmdb:inputText',
			valueKind: 'text',
			operator: 'isNotEmpty'
		});
		const containsRule = JSON.stringify({
			logicId: 'rt-contains',
			sourceFieldName: 'fullname',
			sourceFieldType: 'fmdb:inputText',
			valueKind: 'text',
			operator: 'contains',
			value: 'vip'
		});

		createPublishedLiveFormPage(`text-live-${Date.now()}`, 'Text live form', [
			getInputTextNode({name: 'fullname', title: 'fullname'}),
			{
				...getInputTextNode({name: 'details', title: 'details'}),
				properties: [
					{name: 'jcr:title', value: 'details', language: 'en'},
					{name: 'logics', values: [filledRule]}
				]
			},
			{
				...getInputTextNode({name: 'vip-code', title: 'vip-code'}),
				properties: [
					{name: 'jcr:title', value: 'vip-code', language: 'en'},
					{name: 'logics', values: [containsRule]}
				]
			}
		]).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// Both targets start hidden; waiting for the data attribute also guarantees
			// hydration has run, so typing is picked up by the logic listeners.
			form.get().find('[data-fmdb-node-name="details"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('not.be.visible');
			form.get().find('[data-fmdb-node-name="vip-code"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('not.be.visible');

			// Whitespace-only input is still empty for the text operators.
			form.get().find('input[name="fullname"]').type('   ');
			form.get().find('[data-fmdb-node-name="details"]').should('not.be.visible');

			form.get().find('input[name="fullname"]').clear();
			form.get().find('input[name="fullname"]').type('John');
			form.get().find('[data-fmdb-node-name="details"]').should('be.visible');
			form.get().find('[data-fmdb-node-name="vip-code"]').should('not.be.visible');

			form.get().find('input[name="fullname"]').clear();
			form.get().find('input[name="fullname"]').type('vip customer');
			form.get().find('[data-fmdb-node-name="details"]').should('be.visible');
			form.get().find('[data-fmdb-node-name="vip-code"]').should('be.visible');

			form.get().find('input[name="fullname"]').clear();
			form.get().find('[data-fmdb-node-name="details"]').should('not.be.visible');
			form.get().find('[data-fmdb-node-name="vip-code"]').should('not.be.visible');
		});
	});
});
