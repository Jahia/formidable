import type {JahiaNode} from '../../support/fixtures';
import {
	assertContentIntegrityClean,
	formatIntegrityScanResults,
	runContentIntegrityScan
} from '../../support/contentIntegrity';
import {
	getInputTextNode
} from '../../support/fixtures';
import {FORMIDABLE_TEST_SITE} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

const SAVE_TO_JCR_ACTION: JahiaNode = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
};

const RESULTS_STRUCTURE_CHECKS = ['FormResultsParentIntegrityCheck'];

describe('Content integrity - 61 Negative detection', () => {
	useFormidableSite();

	it('detects a corrupted formResults node when the submissions child is removed', () => {
		const formName = `content-integrity-negative-${Date.now()}`;
		const resultsRootPath = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}`;

		createPublishedLiveFormPage(
			formName,
			'Content integrity negative detection',
			[
				getInputTextNode({
					name: 'fullName',
					title: 'Full name',
					required: true,
					placeholder: 'Full name'
				})
			],
			`${formName}-page`,
			'Content integrity negative detection page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Alice Integrity').shouldBeValid();
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');

			assertContentIntegrityClean({startNode: resultsRootPath, workspace: 'LIVE', checksToRun: RESULTS_STRUCTURE_CHECKS});

			cy.executeGroovy('groovy/removeSubmissionsChild.groovy', {
				'__RESULTS_PATH__': resultsRootPath
			});

			runContentIntegrityScan({
				startNode: resultsRootPath,
				workspace: 'LIVE',
				checksToRun: RESULTS_STRUCTURE_CHECKS
			}).then(results => {
				expect(results.totalErrorCount).to.be.greaterThan(0, formatIntegrityScanResults(results));

				const matchingError = results.errors.find(error =>
					error.checkName === 'FormResultsParentIntegrityCheck' &&
					error.errorType === 'MISSING_REQUIRED_CHILD' &&
					error.nodePath === resultsRootPath
				);

				expect(matchingError, formatIntegrityScanResults(results)).to.exist;
			});
		});
	});
});
