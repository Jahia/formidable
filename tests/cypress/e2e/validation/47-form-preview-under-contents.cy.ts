import {getStartedModuleVersion} from '@jahia/cypress';
import {CONTENT_PATH} from '../../support/constants';
import {createFormNode, FORMIDABLE_TEST_SITE, getInputTextNode, INPUT_TEXT_SIMPLE} from '../../support/fixtures';
import {useFormidableSite} from './support';

// The default module picks a node's hidden.visualEdit view in the edit frame from 8.11.0 (Jahia/default#164).
const VISUAL_EDIT_DEFAULT_VERSION = [8, 11, 0];

// A module version as the dashboard reports it: 8.11.0, 8.11.0-SNAPSHOT or 8.11.0.SNAPSHOT.
const isAtLeast = (version: string, minimum: number[]) => {
	const parts = version.split(/[.-]/).map(Number);
	const index = minimum.findIndex((value, i) => parts[i] !== value);
	return index === -1 || parts[index] > minimum[index];
};

const contentTemplateUrl = (mode: 'render' | 'editframe', formName: string) =>
	`/cms/${mode}/default/en/sites/${FORMIDABLE_TEST_SITE.key}/contents/${formName}.content-template.html`;

// The opening tag of every Page Builder box of the frame on one path.
const moduleBoxesOn = (body: string, path: string) => {
	const tags: string[] = body.match(/<div\b[^>]*\bjahiatype="module"[^>]*>/g) ?? [];
	return tags.filter(tag => tag.includes(`path="${path}"`));
};

// The preview of a node that is not a page (a form under /contents) goes through the platform's
// content template. Until #293 the JSP test template set the test site sits on shipped a clone of
// that template naming a view it did not have: the preview came back as an empty 200 — no
// exception, one WARN in jahia.log, nothing in the suite noticed. A non-empty body is the guard.
describe('Validation - 47 Form preview under /contents', () => {
	useFormidableSite();

	it('renders the form through the platform content template', () => {
		const formName = 'preview-under-contents';
		createFormNode(formName, 'Preview under contents', [getInputTextNode(INPUT_TEXT_SIMPLE)]);

		cy.request({
			url: contentTemplateUrl('render', formName),
			retryOnStatusCodeFailure: true
		}).then(response => {
			expect(response.status).to.equal(200);
			expect(response.body, 'the preview is a page, not an empty body').to.contain('<html');
			expect(response.body, 'the form is rendered').to.contain('fmdb-form');
			expect(response.body, 'the field is rendered').to.contain(`name="${INPUT_TEXT_SIMPLE.name}"`);
		});
	});

	// The same path in the Page Builder renders the form's hidden.visualEdit view: a column around
	// the default view, which it includes so the platform draws one box around the form, not two.
	// Preview and live must never carry that authoring column.
	it('keeps the Page Builder column of the form out of the preview', () => {
		const formName = 'visual-edit-preview';
		createFormNode(formName, 'Visual edit preview', [getInputTextNode(INPUT_TEXT_SIMPLE)]);

		cy.request({
			url: contentTemplateUrl('render', formName),
			retryOnStatusCodeFailure: true
		}).then(response => {
			expect(response.body, 'the form is rendered').to.contain('fmdb-form');
			expect(response.body, 'no authoring column in preview').not.to.contain('fmdb-visual-edit');
		});
	});

	it('renders the form in its column, in one Page Builder box, in the edit frame', function () {
		getStartedModuleVersion('default').then((version: string) => {
			if (!isAtLeast(version, VISUAL_EDIT_DEFAULT_VERSION)) {
				cy.log(`default ${version} renders no hidden.visualEdit view in the edit frame`);
				this.skip();
			}
		});

		const formName = 'visual-edit-frame';
		const formPath = `${CONTENT_PATH}/${formName}`;
		createFormNode(formName, 'Visual edit frame', [getInputTextNode(INPUT_TEXT_SIMPLE)]);

		cy.request({
			url: contentTemplateUrl('editframe', formName),
			retryOnStatusCodeFailure: true
		}).then(response => {
			expect(response.body, 'the authoring column').to.contain('class="fmdb-visual-edit"');
			expect(response.body, 'the form in edit mode').to.contain('data-fmdb-edit-mode="true"');
			expect(response.body, 'the field is rendered').to.contain(`name="${INPUT_TEXT_SIMPLE.name}"`);
			expect(moduleBoxesOn(response.body, formPath), 'one Page Builder box on the form').to.have.length(1);
		});
	});
});
