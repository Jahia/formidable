import type {JahiaNode} from '../../support/fixtures';
import {
	assertContentIntegrityClean,
	formatIntegrityScanResults,
	runContentIntegrityScan
} from '../../support/contentIntegrity';
import {
	FORMIDABLE_TEST_SITE,
	getInputTextNode,
	getLatestLiveFormSubmission
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from '../support/useFormidableSite';

const SAVE_TO_JCR_ACTION: JahiaNode = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
};

describe('Content integrity - 63 Submission deletion detection', () => {
	useFormidableSite();

	it('detects a missing submission data child after targeted deletion', () => {
		const formName = `content-integrity-submission-delete-${Date.now()}`;
		const resultsRootPath = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}`;

		createPublishedLiveFormPage(
			formName,
			'Content integrity submission deletion',
			[
				getInputTextNode({
					name: 'fullName',
					title: 'Full name',
					required: true,
					placeholder: 'Full name'
				})
			],
			`${formName}-page`,
			'Content integrity submission deletion page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Bob Integrity').shouldBeValid();
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');

			assertContentIntegrityClean({startNode: resultsRootPath, workspace: 'LIVE'});

			getLatestLiveFormSubmission(formName).then(({path}) => {
				cy.executeGroovy('groovy/removeSubmissionDataChild.groovy', {
					'__SUBMISSION_PATH__': path
				});

				runContentIntegrityScan({startNode: resultsRootPath, workspace: 'LIVE'}).then(results => {
					expect(results.totalErrorCount).to.be.greaterThan(0, formatIntegrityScanResults(results));

					const matchingError = results.errors.find(error =>
						error.checkName === 'FormSubmissionStructureIntegrityCheck' &&
						error.errorType === 'MISSING_REQUIRED_CHILD' &&
						error.nodePath === path
					);

					expect(matchingError, formatIntegrityScanResults(results)).to.exist;
				});
			});
		});
	});
});
