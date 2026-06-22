import type {JahiaNode} from '../../support/fixtures';
import {assertContentIntegrityClean} from '../../support/contentIntegrity';
import {
	FORMIDABLE_TEST_SITE,
	getInputTextNode
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {CONTENT_PATH, SITE_HOME_PATH} from '../../support/constants';
import {useFormidableSite} from '../support/useFormidableSite';

const SAVE_TO_JCR_ACTION: JahiaNode = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: []
};

describe('Content integrity - 64 Clean deletion', () => {
	useFormidableSite();

	it('keeps the site clean after deleting a form, its page, and its stored results', () => {
		const formName = `content-integrity-clean-delete-${Date.now()}`;
		const pageName = `${formName}-page`;
		const formPath = `${CONTENT_PATH}/${formName}`;
		const pagePath = `${SITE_HOME_PATH}/${pageName}`;
		const resultsRootPath = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}`;

		createPublishedLiveFormPage(
			formName,
			'Content integrity clean deletion',
			[
				getInputTextNode({
					name: 'fullName',
					title: 'Full name',
					required: true,
					placeholder: 'Full name'
				})
			],
			pageName,
			'Content integrity clean deletion page',
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.getTextInput('fullName').type('Delete Me').shouldBeValid();
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');

			assertContentIntegrityClean({startNode: formPath, workspace: 'EDIT'});
			assertContentIntegrityClean({startNode: resultsRootPath, workspace: 'LIVE'});

			cy.executeGroovy('groovy/deletePathsInDefaultAndLive.groovy', {
				'__PATHS__': `${formPath}|${pagePath}|${resultsRootPath}`
			});

			assertContentIntegrityClean({startNode: `/sites/${FORMIDABLE_TEST_SITE.key}/contents`, workspace: 'EDIT'});
			assertContentIntegrityClean({startNode: `/sites/${FORMIDABLE_TEST_SITE.key}`, workspace: 'LIVE'});
		});
	});
});
