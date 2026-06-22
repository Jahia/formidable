import {getNodeByPath, setNodeProperty} from '@jahia/cypress';
import {
	assertContentIntegrityClean,
	formatIntegrityScanResults,
	runContentIntegrityScan
} from '../../support/contentIntegrity';
import {
	createConditionalLogicForm,
	findConditionalLogicNode
} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

interface NodeByPathResponse {
	data?: {
		jcr?: {
			nodeByPath?: {
				uuid?: string;
			} | null;
		} | null;
	};
}

const getNodeUuidByPath = (path: string): Cypress.Chainable<string> => getNodeByPath(path).then((response: NodeByPathResponse) => {
	const uuid = response.data?.jcr?.nodeByPath?.uuid;
	if (!uuid) {
		throw new Error(`Unable to resolve UUID for '${path}'`);
	}

	return uuid;
});

const storeLogicRule = (
	targetPath: string,
	sourcePath: string,
	logicId: string
) => getNodeUuidByPath(sourcePath).then(sourceNodeId => {
	const storedRule = JSON.stringify({
		logicId,
		sourceNodeId,
		sourceFieldName: 'role',
		sourceFieldType: 'fmdb:select',
		operator: 'in',
		values: ['admin']
	});

	return setNodeProperty(targetPath, 'logics', [storedRule], 'en');
});

const waitForReferencedSourcePath = (targetPath: string, expectedSourcePath: string) => {
	cy.waitUntil(() => findConditionalLogicNode(targetPath).then(node => {
		const logicChild = node?.descendant?.children?.nodes?.[0];
		return logicChild?.property?.refNode?.path === expectedSourcePath;
	}));
};

describe('Content integrity - 62 Conditional logic negative detection', () => {
	useFormidableSite();

	it('detects a missing logicsSrc child for an existing conditional-logic rule', () => {
		const logicId = 'logic-role-admin';

		createConditionalLogicForm(`${Date.now()}-missing-logicsrc`).then(({formPath, rolePath, targetPath}) => {
			storeLogicRule(targetPath, rolePath, logicId);
			waitForReferencedSourcePath(targetPath, rolePath);

			assertContentIntegrityClean({startNode: formPath, workspace: 'EDIT'});

			cy.executeGroovy('groovy/removeLogicSrcChild.groovy', {
				'__TARGET_PATH__': targetPath,
				'__LOGIC_ID__': logicId
			});

			runContentIntegrityScan({startNode: formPath, workspace: 'EDIT'}).then(results => {
				expect(results.totalErrorCount).to.be.greaterThan(0, formatIntegrityScanResults(results));

				const matchingError = results.errors.find(error =>
					error.checkName === 'FormLogicReferenceIntegrityCheck' &&
					error.errorType === 'MISSING_LOGICSRC_ENTRY' &&
					error.nodePath === targetPath
				);

				expect(matchingError, formatIntegrityScanResults(results)).to.exist;
			});
		});
	});
});
