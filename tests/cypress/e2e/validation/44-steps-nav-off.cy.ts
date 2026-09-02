import {createPublishedLiveFormPage, getInputTextNode, getStepNode} from '../../support/fixtures';
import {visitLiveForm} from '../../support/fixtures/forms';
import {useFormidableSite} from './support';

/**
 * The documented contract of "Show steps navigation": enabled, step titles live in
 * the nav bar only; DISABLED, title and description show directly inside each step —
 * and the form still walks one step at a time with per-step validation. The option
 * gates the indicator and the compact (title-less) step view, never the hiding.
 */
describe('Validation - 44 Steps navigation turned off', () => {
	useFormidableSite();

	it('shows the title inside each step, one step at a time, without a nav bar', () => {
		createPublishedLiveFormPage(
			'no-nav-form',
			'No Nav Form',
			[
				getStepNode({
					name: 'identityStep',
					title: 'Identity',
					label: 'Identity',
					children: [getInputTextNode({name: 'fullName', title: 'Full name'})]
				}),
				getStepNode({
					name: 'detailsStep',
					title: 'Details',
					label: 'Details',
					children: [getInputTextNode({name: 'comment', title: 'Comment'})]
				})
			],
			undefined,
			undefined,
			{properties: [{name: 'showStepsNav', value: 'false'}]}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			form.waitUntilHydrated();

			// No nav bar, but the current step carries its own title.
			form.get().find('.fmdb-steps-nav').should('not.exist');
			form.get().find('.fmdb-step-title:visible').should('have.length', 1).and('contain', 'Identity');

			// One step at a time: the second step's field is not reachable yet.
			form.getTextInput('comment').get().should('not.be.visible');

			form.getTextInput('fullName').type('Ada');
			form.nextStep();

			form.get().find('.fmdb-step-title:visible').should('have.length', 1).and('contain', 'Details');
			form.getTextInput('fullName').get().should('not.be.visible');
		});
	});
});
