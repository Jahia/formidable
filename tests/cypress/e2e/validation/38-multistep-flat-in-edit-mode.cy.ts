import {
	createPublishedLiveFormPage,
	getInputTextNode,
	getStepNode,
	visitEditForm,
	visitLiveForm
} from '../../support/fixtures';
import {useFormidableSite} from './support';

/**
 * A multi-step form is authored flat: every step is rendered, stacked under its title,
 * with no navigation bar and no Previous/Next buttons. Clicks in the Page Builder select
 * modules, so a form driving its own navigation there would fight the editing UI; and a
 * contributor must reach step 2 without answering the required fields of step 1. For a
 * visitor nothing changes: one step at a time (crossing validation is covered by spec 34).
 */
describe('Validation - 38 Multi-step form rendered flat while authoring', () => {
	useFormidableSite();

	const createStepFormPage = (suffix: string) => createPublishedLiveFormPage(
		`flat-steps-${suffix}`,
		'Flat Steps Form',
		[
			getStepNode({
				name: 'identityStepFlat',
				title: 'Identity',
				label: 'Identity',
				children: [getInputTextNode({name: 'flatEmployeeCode', title: 'Employee code', required: true})]
			}),
			getStepNode({
				name: 'detailsStepFlat',
				title: 'Details',
				label: 'Details',
				children: [getInputTextNode({name: 'flatComment', title: 'Comment'})]
			})
		]
	);

	it('stacks every step with its title and offers no navigation in edit mode', () => {
		createStepFormPage('edit').then(({pagePath}) => {
			const form = visitEditForm(pagePath);

			form.get().should('have.attr', 'data-fmdb-edit-mode', 'true');
			form.get().find('[data-fmdb-step]:visible').should('have.length', 2);
			form.get().find('.fmdb-step-title').should('have.length', 2);
			// Step 2 is reachable without touching the required field of step 1.
			form.getTextInput('flatComment').get().should('be.visible').and('not.be.disabled');

			form.get().find('.fmdb-steps-nav').should('not.exist');
			form.get().find('.fmdb-next-btn, .fmdb-prev-btn').should('not.exist');
		});
	});

	it('keeps one step at a time for a visitor', () => {
		createStepFormPage('live').then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('[data-fmdb-step]:visible').should('have.length', 1);
			form.getStepIndicators().should('have.length', 2);
			form.getNextButton().get().should('be.visible');
		});
	});
});
