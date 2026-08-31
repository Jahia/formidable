import {createPublishedLiveFormPage, getFieldsetNode, visitEditForm} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * A fieldset that was just created has no children — which is exactly when its Page
 * Builder create buttons are indispensable: hidden.logic emits the placeholder jContent
 * reads the insertable types from, so it must render children or not. A guard used to
 * skip it on an empty fieldset, leaving the contributor no way to ever fill it.
 */
describe('Validation - 42 Empty fieldset stays fillable in the Page Builder', () => {
	useFormidableSite();

	it('offers its create buttons inside a fieldset with no children yet', () => {
		createPublishedLiveFormPage('empty-fieldset-form', 'Empty Fieldset Form', [
			getFieldsetNode({name: 'emptyFieldset', title: 'Empty fieldset', children: []})
		]).then(({pagePath}) => {
			visitEditForm(pagePath);

			// Suffix selector on purpose: the module path is absolute today and becomes
			// reference-scoped (ref@/form/fields/...) once the form renders through its
			// reference — the assertion must survive both.
			cy.get('[jahiatype="module"][path$="/fields/emptyFieldset"]')
				.should('have.length', 1)
				.find('[jahiatype="module"][type="placeholder"]')
				.should('have.length', 1);
		});
	});
});
