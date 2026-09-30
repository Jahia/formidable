import {
	FORMIDABLE_TEST_SITE,
	flushSiteCache,
	getInputFileNode,
	INPUT_FILE_MULTIPLE,
	INPUT_FILE_SIMPLE,
	setUploadAllowedTypes,
	UPLOAD_ALLOWED_TYPES_DEFAULT
} from '../../support/fixtures';
import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

/** The accept attribute of a rendered file input, read from the live page's markup. */
const acceptOf = (body: string, name: string): string | undefined =>
	new RegExp(`<input[^>]*name="${name}"[^>]*accept="([^"]*)"`).exec(body)?.[1]
	?? new RegExp(`<input[^>]*accept="([^"]*)"[^>]*name="${name}"`).exec(body)?.[1];

describe('Form fields - 224 File types allowed by the administrator', () => {
	useFormidableSite();

	after(() => {
		cy.login();
		// The allowed types are instance-global: restore them whatever happened.
		setUploadAllowedTypes(UPLOAD_ALLOWED_TYPES_DEFAULT);
		cy.logout();
	});

	it('offers every field only the types the administrator still allows', () => {
		// The multiple field accepts CSV and PDF, the simple one declares nothing; the list allows PDF and PNG:
		// the first keeps PDF only, the second takes the whole list — never "any file".
		createPublishedLiveFormPage(
			'file-allowed-types-form',
			'File Allowed Types Form',
			[getInputFileNode(INPUT_FILE_MULTIPLE), getInputFileNode(INPUT_FILE_SIMPLE)]
		).then(({livePath}) => {
			const liveUrl = `/en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`;

			setUploadAllowedTypes('pdf,png');
			// The config reaches the services immediately, but an already-rendered live page keeps serving its
			// cached fragments: flush before polling.
			cy.waitUntil(
				() => flushSiteCache()
					.then(() => cy.request(liveUrl))
					.then(response => acceptOf(response.body, INPUT_FILE_MULTIPLE.name!) === 'application/pdf,.pdf'),
				{timeout: 30000, interval: 2000, errorMsg: 'the restricted list never reached the rendering'}
			);

			cy.request(liveUrl).then(response => {
				expect(acceptOf(response.body, INPUT_FILE_SIMPLE.name!), 'a field without types takes the list')
					.to.equal('application/pdf,.pdf,image/png,.png');
			});

			const form = visitLiveForm(livePath);
			const multiple = form.getFileInput(INPUT_FILE_MULTIPLE.name!);
			multiple.attachFileAndWaitForCount('cypress/fixtures/files/sample.csv', 0);
			multiple.attachFileAndWaitForCount('cypress/fixtures/files/document.pdf', 1);
			form.getFileInput(INPUT_FILE_SIMPLE.name!).attachFileAndWaitForCount('cypress/fixtures/files/cats.gif', 0);
		});
	});

	it('lets any file through, in the page and on the server, when the administrator allows */*', () => {
		// An e-mail message is none of the five usual top-level types: */* must still take it. The field declaring no
		// type restricts nothing in the page (no accept list) and the server stores the file.
		createPublishedLiveFormPage(
			'file-any-type-form',
			'File Any Type Form',
			[getInputFileNode(INPUT_FILE_SIMPLE)],
			'file-any-type-form-page',
			'File Any Type Form',
			{actions: [{name: 'storeSubmission', primaryNodeType: 'fmdb:save2jcrAction', properties: []}]}
		).then(({livePath}) => {
			const liveUrl = `/en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`;

			setUploadAllowedTypes('*/*');
			cy.waitUntil(
				() => flushSiteCache()
					.then(() => cy.request(liveUrl))
					.then(response => acceptOf(response.body, INPUT_FILE_SIMPLE.name!) === ''),
				{timeout: 30000, interval: 2000, errorMsg: '*/* never reached the rendering'}
			);

			const form = visitLiveForm(livePath);
			form.getFileInput(INPUT_FILE_SIMPLE.name!).attachFileAndWaitForCount('cypress/fixtures/files/note.eml', 1);
			form.submit();
			form.waitForSubmit().shouldHaveSubmissionMessage('Form submitted successfully!');
		});
	});

	it('refuses every file when the administrator allows no type', () => {
		createPublishedLiveFormPage(
			'file-no-allowed-type-form',
			'File No Allowed Type Form',
			[getInputFileNode(INPUT_FILE_SIMPLE)]
		).then(({livePath}) => {
			const liveUrl = `/en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`;

			setUploadAllowedTypes('');
			cy.waitUntil(
				() => flushSiteCache()
					.then(() => cy.request(liveUrl))
					.then(response => acceptOf(response.body, INPUT_FILE_SIMPLE.name!) === ''),
				{timeout: 30000, interval: 2000, errorMsg: 'the empty list never reached the rendering'}
			);

			const fileInput = visitLiveForm(livePath).getFileInput(INPUT_FILE_SIMPLE.name!);
			fileInput.attachFileAndWaitForCount('cypress/fixtures/files/document.pdf', 0);
			fileInput.getInput().should($input => {
				expect(($input[0] as HTMLInputElement).validationMessage).to.equal('This field accepts no file.');
			});
		});
	});
});
