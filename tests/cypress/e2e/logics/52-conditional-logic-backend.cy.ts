import {ConditionalLogicEditor} from '../../page-object';
import {
	createConditionalLogicForm,
	getConditionalLogicNode,
	parseStoredLogicRule
} from '../../support/fixtures';
import {assertContentIntegrityClean} from '../../support/contentIntegrity';
import {useFormidableSite} from './support';

describe('Form logic - 52 Conditional logic backend sync', () => {
	useFormidableSite();

	it('persists sourceNodeId in JSON and synchronizes logicsSrc weakrefs', () => {
		createConditionalLogicForm(`${Date.now()}-persist`).then(({formPath, rolePath, targetPath}) => {
			const editor = ConditionalLogicEditor.visit(targetPath);
			const logicField = editor.logicField;

			logicField.addRule();
			logicField.selectSource(0, 'role');
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
				expect(storedRule.logicId).to.be.a('string').and.not.be.empty;
				expect(storedRule.sourceNodeId).to.be.a('string').and.not.be.empty;
				expect(storedRule.sourceFieldName).to.equal('role');
				expect(storedRule.sourceFieldType).to.equal('fmdb:select');
				expect(storedRule.operator).to.equal('in');
				expect(storedRule.values).to.deep.equal(['admin']);

				expect(node.descendant?.name).to.equal('logicsSrc');
				expect(node.descendant?.primaryNodeType?.name).to.equal('fmdb:logicList');

				const logicChildren = node.descendant?.children?.nodes ?? [];
				expect(logicChildren).to.have.length(1);

				const logicChild = logicChildren[0];
				expect(logicChild.name).to.equal(storedRule.logicId);
				expect(logicChild.primaryNodeType?.name).to.equal('fmdb:logicSrc');
				expect(logicChild.property?.refNode?.path).to.equal(rolePath);
				expect(logicChild.property?.refNode?.name).to.equal('role');
				expect(logicChild.property?.refNode?.uuid).to.equal(storedRule.sourceNodeId);
			});

			assertContentIntegrityClean({startNode: formPath});
		});
	});
});
