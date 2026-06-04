import {
	getInputFileNode,
	INPUT_FILE_MULTIPLE
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

describe('Form fields - 21 File multiple and restrictions', () => {
	useFormidableSite();

	it('allows multiple files and keeps only files matching restrictions', () => {
		createPublishedLiveFormPage(
			'file-restrictions-form',
			'File Restrictions Form',
			[getInputFileNode(INPUT_FILE_MULTIPLE)]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fileInput = form.getFileInput(INPUT_FILE_MULTIPLE.name!);

			fileInput
				.shouldBeVisible()
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
		});
	});
});
