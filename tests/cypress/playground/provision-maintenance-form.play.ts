/**
 * One-off playground add-on for manually testing PR #198 (read-only maintenance):
 * provisions a published live form WITHOUT any repository-writing action, so it
 * must keep working while the platform is in full read-only mode — the contrast
 * case to the standard playground forms (all save-to-JCR, all blocked). It goes
 * in the playground's folder of each playground site, created here if the main
 * script has not run yet.
 * Run with: yarn playground:maintenance (or the cypress --spec equivalent).
 */
import {getInputTextNode, getTextareaNode} from '../support/fixtures';
import {createPublishedLiveFormPage} from '../support/fixtures/forms';
import {ensurePlaygroundFolders, PLAYGROUND_SITES, playgroundFormsPathOf} from './sites';

describe('Playground - provision the maintenance-friendly form', () => {
	before(() => {
		cy.login();
		PLAYGROUND_SITES.forEach(site => ensurePlaygroundFolders(site));
	});

	it('provisions a live form without repository-writing actions on each site', () => {
		PLAYGROUND_SITES.forEach(site => {
			createPublishedLiveFormPage(
				'maintenance-free',
				'Maintenance-friendly form (no JCR action)',
				[
					getInputTextNode({name: 'fullName', title: 'Full name', required: true}),
					getTextareaNode({name: 'message', title: 'Message'})
				],
				undefined,
				undefined,
				{site, parentPath: playgroundFormsPathOf(site.key), publishLanguages: ['en', 'fr']}
			).then(({livePath}) => cy.log(`${site.title} maintenance-friendly form: /en/sites/${site.key}/${livePath}`));
		});
	});
});
