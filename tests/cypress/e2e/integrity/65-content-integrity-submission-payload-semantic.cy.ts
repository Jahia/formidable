import type {JahiaNode} from '../../support/fixtures';
import {
	assertContentIntegrityClean,
	formatIntegrityScanResults,
	runContentIntegrityScan
} from '../../support/contentIntegrity';
import {
	FORMIDABLE_TEST_SITE,
	getInputFileNode,
	getInputTextNode,
	getLatestLiveFormSubmission
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

const SAVE_TO_JCR_ACTION: JahiaNode = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
};

describe('Content integrity - 65 Submission payload semantics', () => {
	useFormidableSite();

	it('detects unexpected data properties and file folders under a saved submission', () => {
		const formName = `content-integrity-payload-${Date.now()}`;
		const resultsRootPath = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}`;

		createPublishedLiveFormPage(
			formName,
			'Content integrity payload semantics',
			[
				getInputTextNode({
					name: 'fullName',
					title: 'Full name',
					required: true,
					placeholder: 'Full name'
				}),
				getInputFileNode({
					name: 'resume',
					title: 'Resume',
					required: true,
					accept: ['application/pdf']
				})
			],
			`${formName}-page`,
			'Content integrity payload semantics page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Semantic Integrity').shouldBeValid();
			form.getFileInput('resume')
				.attachFile('cypress/fixtures/files/document.pdf')
				.shouldHaveSelectedFile('document.pdf');
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');

			assertContentIntegrityClean({startNode: resultsRootPath, workspace: 'LIVE'});

			getLatestLiveFormSubmission(formName).then(({path}) => {
				cy.executeGroovy('groovy/addUnexpectedSubmissionDataProperty.groovy', {
					'__SUBMISSION_PATH__': path,
					'__PROPERTY_NAME__': 'ghostField',
					'__PROPERTY_VALUE__': 'malicious value'
				});
				cy.executeGroovy('groovy/addUnexpectedSubmissionFileFolder.groovy', {
					'__SUBMISSION_PATH__': path,
					'__FOLDER_NAME__': 'ghostFile'
				});

				runContentIntegrityScan({startNode: resultsRootPath, workspace: 'LIVE'}).then(results => {
					expect(results.totalErrorCount).to.be.greaterThan(0, formatIntegrityScanResults(results));

					const undeclaredDataError = results.errors.find(error =>
						error.checkName === 'FormSubmissionPayloadIntegrityCheck' &&
						error.errorType === 'UNDECLARED_SUBMISSION_FIELD' &&
						error.nodePath === path
					);

					const undeclaredFileFolderError = results.errors.find(error =>
						error.checkName === 'FormSubmissionPayloadIntegrityCheck' &&
						error.errorType === 'UNDECLARED_FILE_STORAGE_FIELD' &&
						error.nodePath === path
					);

					expect(undeclaredDataError, formatIntegrityScanResults(results)).to.exist;
					expect(undeclaredFileFolderError, formatIntegrityScanResults(results)).to.exist;
				});
			});
		});
	});
});
