import {
	createPublishedLiveFormPage,
	getEmailNotificationActionNode,
	getInputTextNode,
	getSaveToJcrActionNode,
	visitEditForm,
	visitLiveForm
} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * A form placed on a page shows its actions while authoring: the Page Builder renders the
 * form's action list as a zone under the buttons — one card per action (title, telling
 * parameter, type description), in execution order, and the list's own create button, the
 * placeholder jContent reads the accepted type from. A form without any action is called
 * out, since its submissions go nowhere. Nothing of the zone exists in live.
 */
describe('Validation - 47 Form actions zone in the Page Builder', () => {
	useFormidableSite();

	it('lists the actions of the form with their create button, in edit mode only', () => {
		createPublishedLiveFormPage(
			'actions-zone-form',
			'Actions Zone Form',
			[getInputTextNode({name: 'zoneField', title: 'Zone field'})],
			undefined,
			undefined,
			{
				actions: [
					getEmailNotificationActionNode({name: 'notifySales', title: 'Notify sales', to: 'sales@example.com'}),
					getSaveToJcrActionNode()
				]
			}
		).then(({pagePath, livePath}) => {
			visitEditForm(pagePath);

			cy.get('.fmdb-authoring-actions').should('have.length', 1).within(() => {
				cy.get('.fmdb-authoring-action').should('have.length', 2);
				// Execution order: the email first, the save second.
				cy.get('.fmdb-authoring-action').eq(0).within(() => {
					cy.get('.fmdb-authoring-action-title').should('have.text', 'Notify sales');
					cy.get('.fmdb-authoring-action-detail').should('have.text', 'sales@example.com');
					// The type description is the tooltip the engine declares for the Content Editor.
					cy.get('.fmdb-authoring-action-description').should('contain.text', 'Sends an email notification');
				});
				cy.get('.fmdb-authoring-action').eq(1).within(() => {
					// No title given: the type label stands in, and this type has no telling parameter.
					cy.get('.fmdb-authoring-action-title').should('have.text', 'Save to JCR');
					cy.get('.fmdb-authoring-action-detail').should('not.exist');
				});
				cy.get('.fmdb-authoring-actions-empty').should('not.exist');
			});

			// The list's own module carries the create placeholder, restricted to the action
			// mixin: jContent turns it into one "New Form Action" button, then the type chooser.
			// Suffix selector: the path is reference-scoped (ref@/form/actions).
			cy.get('[jahiatype="module"][path$="/actions"]')
				.should('have.length', 1)
				.find('[jahiatype="module"][type="placeholder"][nodetypes*="fmdbmix:formAction"]')
				.should('have.length', 1);

			// Live: the visitor's form only.
			visitLiveForm(livePath);
			cy.get('.fmdb-authoring-actions').should('not.exist');
		});
	});

	it('calls out a form without any action', () => {
		createPublishedLiveFormPage('no-action-form', 'No Action Form', [
			getInputTextNode({name: 'lonelyField', title: 'Lonely field'})
		]).then(({pagePath}) => {
			visitEditForm(pagePath);

			cy.get('.fmdb-authoring-actions').should('have.length', 1).within(() => {
				cy.get('.fmdb-authoring-action').should('not.exist');
				cy.get('.fmdb-authoring-actions-empty').should('be.visible');
			});
		});
	});
});
