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
			// The authoring zone around the field list exists only while authoring.
			form.get().find('.fmdb-form-fields').should('have.length', 1);
		});
	});

	it('exposes each step as a single Page Builder module, with a New content button only while empty', () => {
		createPublishedLiveFormPage(
			'flat-steps-modules',
			'Flat Steps Form',
			[
				getStepNode({
					name: 'filledStep',
					title: 'Filled',
					label: 'Filled',
					children: [getInputTextNode({name: 'flatFilledField', title: 'Filled field'})]
				}),
				getStepNode({name: 'emptyStep', title: 'Empty', label: 'Empty'})
			]
		).then(({pagePath, formPath}) => {
			visitEditForm(pagePath);

			// One module per step: the step view renders its children read-only, so the
			// Page Builder gets one box per node instead of two stacked boxes.
			cy.get(`[jahiatype="module"][path="${formPath}/fields/filledStep"]`)
				.should('have.length', 1)
				// A step with children is fed through their insertion points: no button row of its own.
				.find('[jahiatype="module"][type="placeholder"]')
				.should('not.exist');
			// An empty step owns its "New content" buttons, otherwise nothing could ever be added to it.
			cy.get(`[jahiatype="module"][path="${formPath}/fields/emptyStep"]`)
				.should('have.length', 1)
				.find('[jahiatype="module"][type="placeholder"]')
				.should('have.length', 1);
			// The field list has steps, so no button row of its own either.
			cy.get(`[jahiatype="module"][path="${formPath}/fields"]`)
				.find('[jahiatype="module"][type="placeholder"]')
				.should('have.length', 1);
		});
	});

	it('keeps one step at a time for a visitor', () => {
		createStepFormPage('live').then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('[data-fmdb-step]:visible').should('have.length', 1);
			form.getStepIndicators().should('have.length', 2);
			form.getNextButton().get().should('be.visible');
			form.get().find('.fmdb-form-fields').should('not.exist');
		});
	});
});
