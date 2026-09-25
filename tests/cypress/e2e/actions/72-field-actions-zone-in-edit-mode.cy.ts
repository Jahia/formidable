import {
	createPublishedLiveFormPage,
	getBlockedWordsFieldActionNode,
	getInputEmailNode,
	getInputTextNode,
	getSaveToJcrActionNode,
	visitEditForm,
	visitLiveForm,
	withFieldActions
} from '../../support/fixtures';
import {useFormidableSite} from '../support/useFormidableSite';

/**
 * A field carrying actions shows them while authoring: the Page Builder renders the field's action
 * list as a zone under the field — one card per action in execution order, each with the settings
 * the contributor chose (when it runs, what a refusal does, what an unanswered check means) — and
 * the list's own create button, whose module declares the accepted type to jContent. A field whose
 * switch is on but whose list is empty is called out; a field without the switch has no zone. The
 * wrapper of a field with actions carries the marker the visitor's page reads — blur when any
 * action runs as the field is left, submit when every one waits — on every surface, and no marker
 * when nothing checks the field. Nothing of the zone exists in live or in preview, not even when its
 * views are requested directly, as fragments.
 */
describe('Actions - 72 Field actions zone in the Page Builder', () => {
	useFormidableSite();

	it('lists the actions of a field under it with their settings and create button, in edit mode only', () => {
		createPublishedLiveFormPage(
			'field-actions-zone-form',
			'Field Actions Zone Form',
			[
				withFieldActions(getInputEmailNode({name: 'email', title: 'Email'}), [
					getBlockedWordsFieldActionNode({name: 'noSpam', title: 'No spam', words: ['spam']}),
					getBlockedWordsFieldActionNode({
						name: 'riskyDomain',
						title: 'Risky domain',
						words: ['nowhere'],
						trigger: 'submit',
						severity: 'warn',
						whenUnavailable: 'reject'
					})
				]),
				withFieldActions(getInputTextNode({name: 'switchedOn', title: 'Switched on, nothing yet'})),
				getInputTextNode({name: 'plain', title: 'Plain field'})
			],
			undefined,
			undefined,
			{actions: [getSaveToJcrActionNode()]}
		).then(({pagePath, livePath, formPath}) => {
			visitEditForm(pagePath);

			// The zone is inside the field's wrapper, so it moves with the field's box.
			cy.get('[data-fmdb-node-name="email"]').should('have.attr', 'data-fmdb-field-action', 'blur');
			cy.get('[data-fmdb-node-name="email"] .fmdb-authoring-field-actions').should('have.length', 1).within(() => {
				cy.get('.fmdb-authoring-action').should('have.length', 2);
				// Execution order, and the settings read from the node — the defaults for the first action.
				cy.get('.fmdb-authoring-action').eq(0)
					.should('have.attr', 'data-fmdb-action-type', 'fmdbsample:blockedWordsAction')
					.within(() => {
						cy.get('.fmdb-authoring-action-title').should('have.text', 'No spam');
						cy.get('.fmdb-authoring-action-badge[data-fmdb-setting="trigger"]').should('have.text', 'When the visitor leaves the field');
						cy.get('.fmdb-authoring-action-badge[data-fmdb-setting="severity"]').should('have.text', 'Blocks the submission');
						cy.get('.fmdb-authoring-action-badge[data-fmdb-setting="whenUnavailable"]').should('have.text', 'Accepted if the check cannot run');
						// Third-party contract: the type's label, tooltip and icon come from ITS module.
						cy.get('.fmdb-authoring-action-description').should('not.be.empty');
						cy.get('.fmdb-authoring-action-icon').should('have.attr', 'src')
							.and('include', '/modules/formidable-test-module-samples-java/icons/fmdbsample_blockedWordsAction.png');
					});
				cy.get('.fmdb-authoring-action').eq(1).within(() => {
					cy.get('.fmdb-authoring-action-title').should('have.text', 'Risky domain');
					cy.get('.fmdb-authoring-action-badge[data-fmdb-setting="trigger"]').should('have.text', 'At submission');
					cy.get('.fmdb-authoring-action-badge[data-fmdb-setting="severity"]').should('have.text', 'Warns only');
					cy.get('.fmdb-authoring-action-badge[data-fmdb-setting="whenUnavailable"]').should('have.text', 'Refused if the check cannot run');
				});
				cy.get('.fmdb-authoring-actions-empty').should('not.exist');
			});

			// The switch is on but nothing checks the field yet: called out, and no marker for the visitor's page.
			cy.get('[data-fmdb-node-name="switchedOn"]').should('not.have.attr', 'data-fmdb-field-action');
			cy.get('[data-fmdb-node-name="switchedOn"] .fmdb-authoring-field-actions').should('have.length', 1).within(() => {
				cy.get('.fmdb-authoring-action').should('not.exist');
				cy.get('.fmdb-authoring-actions-empty').should('be.visible');
			});

			// No switch: no zone, no marker.
			cy.get('[data-fmdb-node-name="plain"]').should('not.have.attr', 'data-fmdb-field-action');
			cy.get('[data-fmdb-node-name="plain"] .fmdb-authoring-field-actions').should('not.exist');

			// The form's own actions zone is untouched by the field zones: one, under the buttons.
			cy.get('.fmdb-authoring-actions:not(.fmdb-authoring-field-actions)').should('have.length', 1);

			// The list's own module declares the accepted type — the field-action mixin, which jContent
			// turns into one create button, then the type chooser — and holds the create placeholder.
			cy.get('[jahiatype="module"][path$="/fields/email/actions"]')
				.should('have.length', 1)
				.should('have.attr', 'nodetypes', 'fmdbmix:fieldAction')
				.find('[jahiatype="module"][type="placeholder"]')
				.should('have.length', 1);

			// The authoring views guard themselves: asked for directly in live or in preview, they
			// render — successfully — nothing of the zone (fragment URLs, as spec 71 checks them).
			[
				{mode: 'live', base: '/cms/render/live/en'},
				{mode: 'preview', base: '/cms/preview/default/en'}
			].forEach(({mode, base}) => {
				[`${formPath}/fields/email/actions`, `${formPath}/fields/email/actions/noSpam`].forEach(path => {
					cy.request(`${base}${path}.hidden.authoring.html.ajax`).then(response => {
						expect(response.status, `${mode} render of ${path}`).to.equal(200);
						expect(response.body, `${mode} body of ${path}`).not.to.contain('fmdb-authoring-action');
					});
				});
			});

			// Live: the visitor's form only, the marker on it.
			visitLiveForm(livePath);
			cy.get('.fmdb-authoring-actions').should('not.exist');
			cy.get('[data-fmdb-node-name="email"]').should('have.attr', 'data-fmdb-field-action', 'blur');
			cy.get('[data-fmdb-node-name="switchedOn"]').should('not.have.attr', 'data-fmdb-field-action');
		});
	});

	it('marks a field whose every action waits for the submission with the submit trigger', () => {
		createPublishedLiveFormPage('submit-only-form', 'Submit Only Form', [
			withFieldActions(getInputTextNode({name: 'code', title: 'Code'}), [
				getBlockedWordsFieldActionNode({name: 'paid', words: ['x'], trigger: 'submit'}),
				getBlockedWordsFieldActionNode({name: 'paidToo', words: ['y'], trigger: 'submit'})
			])
		]).then(({livePath}) => {
			visitLiveForm(livePath);
			cy.get('[data-fmdb-node-name="code"]').should('have.attr', 'data-fmdb-field-action', 'submit');
		});
	});
});
