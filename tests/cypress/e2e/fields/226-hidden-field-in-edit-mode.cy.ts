import {createPublishedLiveFormPage, getInputHiddenNode, getInputTextNode, visitEditForm, visitLiveForm} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A hidden input is never shown to the visitor, so its Page Builder box used to have no height: the
 * contributor could reach it only from the content tree. While authoring it draws a line instead — the
 * crossed-out eye, "Hidden field", its title (its name without one) and the value it sends, "No value"
 * when it sends nothing — around the same hidden input. Live and preview keep the bare input.
 */
describe('Form fields - 226 Hidden field in the Page Builder', () => {
	useFormidableSite();

	it('shows a hidden field as a line with its title and value in edit mode only', () => {
		createPublishedLiveFormPage('hidden-field-form', 'Hidden Field Form', [
			getInputTextNode({name: 'visible', title: 'Visible field'}),
			getInputHiddenNode({name: 'campaign', title: 'Campaign', value: 'spring-2026'}),
			getInputHiddenNode({name: 'blank'})
		]).then(({pagePath, livePath}) => {
			visitEditForm(pagePath);

			cy.get('[data-fmdb-node-name="campaign"] .fmdb-authoring-hidden-field').should('be.visible').within(() => {
				cy.get('svg.fmdb-authoring-hidden-field-glyph').should('exist');
				cy.get('.fmdb-authoring-hidden-field-label').should('have.text', 'Hidden field');
				cy.get('.fmdb-authoring-hidden-field-title').should('have.text', 'Campaign');
				cy.get('.fmdb-authoring-hidden-field-value')
					.should('have.text', 'spring-2026')
					.and('not.have.class', 'fmdb-authoring-hidden-field-empty');
				cy.get('input[type="hidden"][name="campaign"]').should('have.value', 'spring-2026');
			});

			// No title: the node name. No value: said so, not an empty gap.
			cy.get('[data-fmdb-node-name="blank"] .fmdb-authoring-hidden-field').should('be.visible').within(() => {
				cy.get('.fmdb-authoring-hidden-field-title').should('have.text', 'blank');
				cy.get('.fmdb-authoring-hidden-field-value.fmdb-authoring-hidden-field-empty').should('have.text', 'No value');
			});

			// The line gives the field's own Page Builder module a box to click.
			cy.get('[jahiatype="module"][path$="/fields/campaign"]').invoke('outerHeight').should('be.greaterThan', 0);

			cy.request(`/cms/preview/default/en${pagePath}.html`).then(response => {
				expect(response.status, 'preview render').to.equal(200);
				expect(response.body, 'preview body').to.contain('name="campaign"');
				expect(response.body, 'preview body').not.to.contain('fmdb-authoring-hidden-field');
			});

			visitLiveForm(livePath);
			cy.get('.fmdb-authoring-hidden-field').should('not.exist');
			cy.get('input[type="hidden"][name="campaign"]').should('have.value', 'spring-2026');
		});
	});
});
