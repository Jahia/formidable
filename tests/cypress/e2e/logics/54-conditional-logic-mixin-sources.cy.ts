import {ConditionalLogicEditor} from '../../page-object';
import {
	createMixinSourcesConditionalLogicForm,
	getConditionalLogicNode,
	getInputTextNode,
	parseStoredLogicRule
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * Add-on field types become conditional-logic sources purely by carrying one of
 * the engine's semantic value-kind mixins in their CND (issues #160/#125).
 * fmdbext:rating and fmdbext:scale carry fmdbmix:numberField, fmdbext:switch and
 * fmdbext:consent carry fmdbmix:booleanField — none of them has any
 * logic-specific code.
 */
describe('Form logic - 54 Mixin-declared add-on sources', () => {
	useFormidableSite();

	it('offers a rating field as source with numeric operators and stores valueKind', () => {
		createMixinSourcesConditionalLogicForm(`${Date.now()}-number`).then(({formPath, ratingPath, targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.openSourceDropdown(0);
			logicField.menuShouldHaveItems(['satisfaction', 'newsletter']);
			logicField.selectMenuItem('satisfaction');

			logicField.openOperatorDropdown(0);
			logicField.menuShouldHaveItems(['equals', 'is less than', 'is greater than', 'is between']);
			logicField.selectMenuItem('is greater than');
			logicField.ruleShouldHaveNumberInputCount(0, 1);

			logicField.selectOperator(0, 'is between');
			logicField.ruleShouldHaveNumberInputCount(0, 2);

			logicField.selectOperator(0, 'is greater than');
			logicField.typeNumberValue(0, '3');
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node => {
				const logicChildren = node.descendant?.children?.nodes ?? [];
				return logicChildren.length === 1 && Boolean(logicChildren[0].property?.refNode?.uuid);
			}));

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				expect(rawLogics).to.have.length(1);

				const storedRule = parseStoredLogicRule(rawLogics[0]);
				expect(storedRule.sourceFieldName).to.equal('satisfaction');
				expect(storedRule.sourceFieldType).to.equal('fmdbext:rating');
				expect(storedRule.valueKind).to.equal('number');
				expect(storedRule.operator).to.equal('gt');
				expect(storedRule.value).to.equal('3');

				const logicChild = node.descendant?.children?.nodes?.[0];
				expect(logicChild?.property?.refNode?.path).to.equal(ratingPath);
			});

			cy.log(`validated number source on ${formPath}`);
		});
	});

	it('offers a switch field as source with isTrue/isFalse and no value widget', () => {
		createMixinSourcesConditionalLogicForm(`${Date.now()}-boolean`).then(({switchPath, targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSource(0, 'newsletter');

			logicField.openOperatorDropdown(0);
			logicField.menuShouldHaveItems(['is true', 'is false']);
			logicField.selectMenuItem('is true');

			// Source type + source + operator only: boolean rules need no value widget.
			logicField.ruleShouldHaveDropdownCount(0, 3);
			logicField.ruleShouldHaveNumberInputCount(0, 0);
			editor.save();

			cy.waitUntil(() => getConditionalLogicNode(targetPath).then(node => {
				const logicChildren = node.descendant?.children?.nodes ?? [];
				return logicChildren.length === 1 && Boolean(logicChildren[0].property?.refNode?.uuid);
			}));

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				expect(rawLogics).to.have.length(1);

				const storedRule = parseStoredLogicRule(rawLogics[0]);
				expect(storedRule.sourceFieldName).to.equal('newsletter');
				expect(storedRule.sourceFieldType).to.equal('fmdbext:switch');
				expect(storedRule.valueKind).to.equal('boolean');
				expect(storedRule.operator).to.equal('isTrue');
				expect(storedRule.value).to.equal(undefined);
				expect(storedRule.values ?? []).to.have.length(0);

				const logicChild = node.descendant?.children?.nodes?.[0];
				expect(logicChild?.property?.refNode?.path).to.equal(switchPath);
			});
		});
	});

	it('evaluates number and boolean rules in the live form with numeric comparison', () => {
		// gt "9" satisfied by picking 10 on the scale proves numeric comparison:
		// lexicographically "10" < "9".
		const scaleRule = JSON.stringify({
			logicId: 'rt-number',
			sourceFieldName: 'satisfaction',
			sourceFieldType: 'fmdbext:scale',
			valueKind: 'number',
			operator: 'gt',
			value: '9'
		});
		const switchRule = JSON.stringify({
			logicId: 'rt-boolean',
			sourceFieldName: 'newsletter',
			sourceFieldType: 'fmdbext:switch',
			valueKind: 'boolean',
			operator: 'isTrue'
		});

		createPublishedLiveFormPage(`mixin-live-${Date.now()}`, 'Mixin live form', [
			{
				// Default scale is 0..10, so the "10" chip exists out of the box.
				name: 'satisfaction',
				primaryNodeType: 'fmdbext:scale',
				properties: [{name: 'jcr:title', value: 'satisfaction', language: 'en'}]
			},
			{
				name: 'newsletter',
				primaryNodeType: 'fmdbext:switch',
				properties: [{name: 'jcr:title', value: 'newsletter', language: 'en'}]
			},
			{
				...getInputTextNode({name: 'nickname', title: 'nickname'}),
				properties: [
					{name: 'jcr:title', value: 'nickname', language: 'en'},
					{name: 'logics', values: [scaleRule]}
				]
			},
			{
				...getInputTextNode({name: 'company', title: 'company'}),
				properties: [
					{name: 'jcr:title', value: 'company', language: 'en'},
					{name: 'logics', values: [switchRule]}
				]
			}
		]).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			// Both targets start hidden; waiting for the data attribute also guarantees
			// hydration has run, so subsequent interactions are picked up by the logic listeners.
			form.get().find('[data-fmdb-node-name="nickname"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('not.be.visible');
			form.get().find('[data-fmdb-node-name="company"]')
				.should('have.attr', 'data-fmdb-logic-hidden', 'true')
				.and('not.be.visible');

			// Scale chips visually hide their radio inputs (sr-only pattern) → force.
			form.get().find('input[name="satisfaction"][value="10"]').check({force: true});
			form.get().find('[data-fmdb-node-name="nickname"]').should('be.visible');

			form.get().find('input[name="satisfaction"][value="5"]').check({force: true});
			form.get().find('[data-fmdb-node-name="nickname"]').should('not.be.visible');

			form.get().find('input[name="newsletter"]').check({force: true});
			form.get().find('[data-fmdb-node-name="company"]').should('be.visible');

			form.get().find('input[name="newsletter"]').uncheck({force: true});
			form.get().find('[data-fmdb-node-name="company"]').should('not.be.visible');
		});
	});
});
