import {createSite, deleteSite, enableModule} from '@jahia/cypress';
import {FORMIDABLE_MODULE_IDS} from '../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	getInputFileNode,
	INPUT_FILE_MULTIPLE
} from '../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../support/fixtures/forms';

describe('File Input Component', () => {
	before(() => {
		deleteSite(FORMIDABLE_TEST_SITE.key);
		createSite(FORMIDABLE_TEST_SITE.key, FORMIDABLE_TEST_SITE.config);
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, FORMIDABLE_TEST_SITE.key));
	});

	beforeEach(() => {
		cy.login();
	});

	afterEach(() => {
		cy.logout();
	});

	it('should keep valid files, ignore invalid ones, and support removal in live mode', () => {
		createPublishedLiveFormPage(
			'file-live-form',
			'File Live Form',
			[getInputFileNode(INPUT_FILE_MULTIPLE)]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fileInput = form.getFileInput(INPUT_FILE_MULTIPLE.name!);

			fileInput
				.shouldBeVisible()
				.shouldHaveLabel(INPUT_FILE_MULTIPLE.title!)
				.shouldBeRequired()
				.shouldBeMultiple()
				.shouldAccept(INPUT_FILE_MULTIPLE.accept!);

			fileInput.attachFile([
				'cypress/fixtures/files/sample.csv',
				'cypress/fixtures/files/invalid.txt'
			]);

			fileInput
				.shouldHaveSelectedFileCount(1)
				.shouldHaveSelectedFile('sample.csv')
				.shouldHaveSelectionNotice('.txt');

			fileInput.attachFile('cypress/fixtures/files/document.pdf');

			fileInput
				.shouldHaveSelectedFileCount(2)
				.shouldHaveSelectedFile('document.pdf');

			fileInput.removeSelectedFile(0);
			fileInput
				.shouldHaveSelectedFileCount(1)
				.shouldHaveSelectedFile('document.pdf');
		});
	});
});
