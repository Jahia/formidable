import {
	findConditionalLogicNode,
	getConditionalLogicNode,
	importConditionalLogicForm,
	importDuplicateSourceNameConditionalLogicForm,
	parseStoredLogicRule
} from '../../support/fixtures';
import {assertContentIntegrityClean} from '../../support/contentIntegrity';
import {useFormidableSite} from '../support/useFormidableSite';

const waitForReferencedSourcePath = (targetPath: string, expectedSourcePath: string) => {
	cy.waitUntil(() => findConditionalLogicNode(targetPath).then(node => {
		const logicChild = node?.descendant?.children?.nodes?.[0];
		return logicChild?.property?.refNode?.path === expectedSourcePath;
	}));
};

describe('Form logic - 53 Conditional logic import', () => {
	useFormidableSite();

	it('imports a form and rebinds the weakref and sourceNodeId to the imported source node', () => {
		importConditionalLogicForm(`${Date.now()}-happy-path`).then(({
			formPath,
			legacySourceNodeId,
			rolePath,
			targetPath
		}) => {
			waitForReferencedSourcePath(targetPath, rolePath);

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				expect(rawLogics).to.have.length(1);

				const storedRule = parseStoredLogicRule(rawLogics[0]);
				const logicChild = node.descendant?.children?.nodes?.[0];
				const importedRoleUuid = logicChild?.property?.refNode?.uuid;

				expect(storedRule.logicId).to.equal('logic-import-role');
				expect(storedRule.sourceFieldName).to.equal('role');
				expect(storedRule.sourceFieldType).to.equal('fmdb:radio');
				expect(storedRule.sourceNodeId).to.equal(importedRoleUuid);
				expect(storedRule.sourceNodeId).not.to.equal(legacySourceNodeId);
				expect(logicChild?.property?.refNode?.path).to.equal(rolePath);
			});

			assertContentIntegrityClean({startNode: formPath});
		});
	});

	it('imports a form with duplicate source names and keeps the weakref bound to the intended source node', () => {
		importDuplicateSourceNameConditionalLogicForm(`${Date.now()}-duplicates`).then(({
			formPath,
			firstSourcePath,
			legacySourceNodeId,
			secondSourcePath,
			targetPath
		}) => {
			waitForReferencedSourcePath(targetPath, secondSourcePath);

			getConditionalLogicNode(targetPath).then(node => {
				const rawLogics = node.properties?.find(property => property.name === 'logics')?.values ?? [];
				expect(rawLogics).to.have.length(1);

				const storedRule = parseStoredLogicRule(rawLogics[0]);
				const logicChild = node.descendant?.children?.nodes?.[0];
				const importedSourceUuid = logicChild?.property?.refNode?.uuid;

				expect(storedRule.logicId).to.equal('logic-import-duplicate');
				expect(storedRule.sourceFieldName).to.equal('select-an-option');
				expect(storedRule.sourceFieldType).to.equal('fmdb:radio');
				expect(storedRule.sourceNodeId).to.equal(importedSourceUuid);
				expect(storedRule.sourceNodeId).not.to.equal(legacySourceNodeId);
				expect(logicChild?.property?.refNode?.path).to.equal(secondSourcePath);
				expect(logicChild?.property?.refNode?.path).not.to.equal(firstSourcePath);
			});

			assertContentIntegrityClean({startNode: formPath});
		});
	});
});
