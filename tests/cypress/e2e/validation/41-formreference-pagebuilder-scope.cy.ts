import {createPublishedLiveFormPage, getInputTextNode, visitEditForm, visitLiveForm} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * A form on a page renders THROUGH its reference (contextualized node, read-only at its
 * root), the way the core's jmix:nodeReference view renders any content reference. The
 * Page Builder box a contributor reaches is then the reference's — deleting removes the
 * reference and not the form, Go to source is offered — while the form's children keep
 * their own (reference-scoped) modules and stay editable.
 */
describe('Validation - 41 Form reference owns its Page Builder box', () => {
	useFormidableSite();

	it('scopes the editable modules under the reference, not the form', () => {
		const formName = 'ref-scope-form';

		createPublishedLiveFormPage(formName, 'Ref Scope Form', [
			getInputTextNode({name: 'refScopedField', title: 'Ref scoped field'})
		]).then(({formPath, pagePath}) => {
			visitEditForm(pagePath);

			// The reference has its own module, the form none at its absolute path: menus
			// (Delete, Go to source) land on the reference. Its children render through
			// the dereference syntax (reference@/...), still editable.
			cy.get(`[jahiatype="module"][path="${pagePath}/pagecontent/${formName}-reference"]`).should('exist');
			cy.get(`[jahiatype="module"][path="${formPath}"]`).should('not.exist');
			cy.get(`[jahiatype="module"][path^="${pagePath}/pagecontent/${formName}-reference@/"]`).should('exist');
		});
	});

	it('keeps the live rendering and its hydration through the reference', () => {
		const formName = 'ref-live-form';

		createPublishedLiveFormPage(formName, 'Ref Live Form', [
			getInputTextNode({name: 'refLiveField', title: 'Ref live field'})
		]).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.get().find('input[name="refLiveField"]').should('be.visible');
		});
	});
});
