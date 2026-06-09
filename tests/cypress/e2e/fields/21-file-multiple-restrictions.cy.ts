import {
	getInputFileNode,
	INPUT_FILE_MULTIPLE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 21 File multiple and restrictions', () => {
	useFormidableSite();

	it('keeps valid files, ignores invalid ones, and merges a second valid selection', () => {
		createPublishedLiveFormPage(
			'file-restrictions-form',
			'File Restrictions Form',
			[getInputFileNode(INPUT_FILE_MULTIPLE)]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fileInput = form.getFileInput(INPUT_FILE_MULTIPLE.name!);

			fileInput
				.shouldBeRequired()
				.shouldBeMultiple();

			fileInput.attachFileAndWaitForCount([
				'cypress/fixtures/files/document.pdf',
				'cypress/fixtures/files/invalid.txt'
			], 1);

			fileInput
				.shouldHaveSelectedFileCount(1)
				.shouldHaveSelectedFile('document.pdf')
				.shouldHaveSelectionNotice('.txt');

			fileInput.attachFileAndWaitForCount('cypress/fixtures/files/sample.csv', 2);
			fileInput
				.shouldHaveSelectedFileCount(2)
				.shouldHaveSelectedFile('sample.csv');
		});
	});
});
