import {createPublishedLiveFormPage, getInputTextNode, visitEditForm} from '../../support/fixtures';
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
		]).then(({formPath, pagePath, referencePath}) => {
			visitEditForm(pagePath);

			// The reference has its own module, the form none at its absolute path: menus
			// (Delete, Go to source) land on the reference. Its children render through
			// the dereference syntax (reference@/form/...), still editable.
			cy.get(`[jahiatype="module"][path="${referencePath}"]`).should('exist');
			cy.get(`[jahiatype="module"][path="${formPath}"]`).should('not.exist');
			cy.get(`[jahiatype="module"][path^="${referencePath}@/"]`).should('exist');
		});
	});
});
