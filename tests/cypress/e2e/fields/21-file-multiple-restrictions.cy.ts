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

	it('forgets the selected files when the form is reset, by the button or after a submission', () => {
		// The chip list is client state: a native reset empties the input but, left
		// alone, keeps the chips, and the visitor believes the file is still attached (#288).
		createPublishedLiveFormPage(
			'file-reset-form',
			'File Reset Form',
			[getInputFileNode(INPUT_FILE_MULTIPLE)],
			'file-reset-form-page',
			'File Reset Form',
			{
				properties: [
					{name: 'showResetBtn', value: 'true', type: 'BOOLEAN'},
					{name: 'showNewFormBtn', value: 'true', type: 'BOOLEAN'}
				]
			}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const fileInput = form.getFileInput(INPUT_FILE_MULTIPLE.name!);

			fileInput.attachFileAndWaitForCount('cypress/fixtures/files/document.pdf', 1);
			form.reset();
			fileInput.shouldHaveSelectedFileCount(0);
			fileInput.getInput().should($input => {
				expect(($input[0] as HTMLInputElement).files?.length ?? 0, 'native input files').to.equal(0);
			});

			// "Submit another form" brings the form back through the same reset.
			fileInput.attachFileAndWaitForCount('cypress/fixtures/files/document.pdf', 1);
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
			form.getNewFormButton().click();
			form.getFileInput(INPUT_FILE_MULTIPLE.name!)
				.shouldBeVisible()
				.shouldHaveSelectedFileCount(0);
		});
	});
});
