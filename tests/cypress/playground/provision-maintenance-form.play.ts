/**
 * One-off playground add-on for manually testing PR #198 (read-only maintenance):
 * provisions a published live form WITHOUT any repository-writing action, so it
 * must keep working while the platform is in full read-only mode — the contrast
 * case to the three standard playground forms (all save-to-JCR, all blocked).
 * Run with: yarn playground:maintenance (or the cypress --spec equivalent).
 */
import {FORMIDABLE_TEST_SITE, getInputTextNode, getTextareaNode} from '../support/fixtures';
import {createPublishedLiveFormPage} from '../support/fixtures/forms';

describe('Playground - provision the maintenance-friendly form', () => {
	before(() => {
		cy.login();
	});

	it('provisions a live form without repository-writing actions', () => {
		createPublishedLiveFormPage(
			'playground-maintenance-free',
			'Playground - Maintenance-friendly form (no JCR action)',
			[
				getInputTextNode({name: 'fullName', title: 'Full name', required: true}),
				getTextareaNode({name: 'message', title: 'Message'})
			],
			undefined,
			undefined,
			{publishLanguages: ['en', 'fr']}
		).then(({livePath}) => cy.log(`Maintenance-friendly form: /en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`));
	});
});
