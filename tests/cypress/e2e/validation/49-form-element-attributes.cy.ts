import {createPublishedLiveFormPage, visitLiveForm} from '../../support/fixtures/forms';
import {getInputTextNode} from '../../support/fixtures';
import {useFormidableSite} from '../fields/support';

/**
 * The attributes of the rendered <form>. Three of them are a contract with jExperience's tracker,
 * which this package knows nothing about: the form's UUID is its identity there, and two opt-outs
 * keep the tracker from attaching its own submit listener and posting the raw, pre-validation DOM
 * fields. Nothing else in this repository consumes them, which is exactly why they are asserted
 * here — see docs/architecture/jexperience-integration.md, "The identifier".
 */
describe('Form rendering - 49 the form element attributes', () => {
	useFormidableSite();

	it('identifies the form by its UUID and keeps the tracker off it', () => {
		createPublishedLiveFormPage(
			'form-attributes-form',
			'Attributes Form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})]
		).then(({formId, livePath}) => {
			const form = visitLiveForm(livePath);

			// id and name are both the node's UUID: every reader of a form in jExperience — the tracker,
			// the tag picker of the Form mappings screen, that screen's own page lookup — takes the name
			// first and the id second, so they must agree and must be the identity the rule names.
			form.get().should('have.attr', 'id', formId);
			form.get().should('have.attr', 'name', formId);

			// the two opt-outs, one per code path: the initial scan of the Unomi tracker skips a form
			// carrying data-form-id, and jExperience's observer of late forms skips a marked one
			form.get().should('have.attr', 'data-form-id', formId);
			form.get().should('have.attr', 'data-wem-observed', 'true');

			// the landmark takes the title the author gave, never a fallback built from the node name
			form.get().should('have.attr', 'aria-label', 'Attributes Form');
		});
	});
});
