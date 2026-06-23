import type {JahiaNode} from '../../support/fixtures';
import {
	assertContentIntegrityClean,
	formatIntegrityScanResults,
	runContentIntegrityScan
} from '../../support/contentIntegrity';
import {
	FORMIDABLE_TEST_SITE,
	getInputTextNode
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {SITE_HOME_PATH} from '../../support/constants';
import {useFormidableSite} from '../support/useFormidableSite';

const SAVE_TO_JCR_ACTION: JahiaNode = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
};

const RESULTS_PARENT_FORM_CHECKS = ['FormResultsParentIntegrityCheck'];

const ensureGroovySucceeded = (result: unknown) => {
	if (typeof result === 'string' && result.includes('.failed')) {
		throw new Error(`Groovy script failed: ${result}`);
	}

	return result;
};

const setWeakReferenceTarget = (
	workspace: 'default' | 'live',
	sourcePath: string,
	propertyName: string,
	targetPath: string
) => cy.executeGroovy('groovy/setWeakReferencePropertyTarget.groovy', {
	'__WORKSPACE__': workspace,
	'__SOURCE_PATH__': sourcePath,
	'__PROPERTY_NAME__': propertyName,
	'__TARGET_PATH__': targetPath
}).then(ensureGroovySucceeded);

describe('Content integrity - 66 Reference target negative detection', () => {
	useFormidableSite();

	it('detects a formResults parentForm pointing to a node that is not an fmdb:form', () => {
		const formName = `content-integrity-parent-form-${Date.now()}`;
		const pageName = `${formName}-page`;
		const pagePath = `${SITE_HOME_PATH}/${pageName}`;
		const resultsRootPath = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}`;

		createPublishedLiveFormPage(
			formName,
			'Content integrity parentForm negative',
			[
				getInputTextNode({
					name: 'fullName',
					title: 'Full name',
					required: true,
					placeholder: 'Full name'
				})
			],
			pageName,
			'Content integrity parentForm negative page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Parent Form Negative').shouldBeValid();
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');

			assertContentIntegrityClean({startNode: resultsRootPath, workspace: 'LIVE', checksToRun: RESULTS_PARENT_FORM_CHECKS});

			setWeakReferenceTarget('live', resultsRootPath, 'parentForm', pagePath);

			runContentIntegrityScan({
				startNode: resultsRootPath,
				workspace: 'LIVE',
				checksToRun: RESULTS_PARENT_FORM_CHECKS
			}).then(results => {
				expect(results.totalErrorCount).to.be.greaterThan(0, formatIntegrityScanResults(results));

				const matchingError = results.errors.find(error =>
					error.checkName === 'FormResultsParentIntegrityCheck' &&
					error.errorType === 'INVALID_TARGET_NODE_TYPE' &&
					error.nodePath === resultsRootPath
				);

				expect(matchingError, formatIntegrityScanResults(results)).to.exist;
			});
		});
	});
});
