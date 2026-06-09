import {getInputFileNode} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

const TXT_ONLY_FILE_FIELD = {
	name: 'txtOnlyUpload',
	title: 'TXT-only upload',
	accept: ['.txt'],
	required: true
};

const FAKE_TXT_FIXTURES = [
	'catsImageAsFakeTxt.txt',
	'catsMkvAsFakeTxt.txt',
	'sampleOdsAsFakeTxt.txt'
] as const;

describe('Form fields - 29 Fake TXT uploads rejected by backend MIME detection', () => {
	useFormidableSite();

	for (const fileName of FAKE_TXT_FIXTURES) {
		it(`accepts ${fileName} on the front end but rejects it on submission`, () => {
			createPublishedLiveFormPage(
				`file-fake-txt-${fileName.replace(/[^a-zA-Z0-9]/g, '-').toLowerCase()}`,
				`File fake txt ${fileName}`,
				[getInputFileNode(TXT_ONLY_FILE_FIELD)]
			).then(({livePath}) => {
				const form = visitLiveForm(livePath);
				const fileInput = form.getFileInput(TXT_ONLY_FILE_FIELD.name);
				const filePath = `cypress/fixtures/files/${fileName}`;

				fileInput
					.shouldBeRequired()
					.shouldAccept('.txt')
					.attachFile(filePath)
					.shouldHaveSelectedFile(fileName)
					.shouldHaveSelectedFileCount(1);

				form.submit();
				form.waitForSubmit().shouldHaveErrorMessage('FMDB-010');
			});
		});
	}
});
