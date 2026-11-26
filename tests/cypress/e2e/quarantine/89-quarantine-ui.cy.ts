import {createUser, deleteUser, enableModule, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	FORMIDABLE_TEST_SITE,
	QUARANTINE_MANAGER_ROLE,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {STUB_FILE, fields, readHeldSubmissions, runQuarantine, setUploadsRequireQuarantine, submissionWithText} from './support';

const SITE = FORMIDABLE_TEST_SITE.key;
const SITE_PATH = `/sites/${SITE}`;
const QUARANTINE_PAGE = `/jahia/jcontent/${SITE}/en/apps/formidableQuarantine`;
const FORM_TITLE = 'Quarantine UI form';

/** The users of the spec: the manager holds the site role of the quarantine alone; the editor does not hold it. */
const PASSWORD = 'quarUi-Passw0rd!';
const MANAGER = 'quarUiManager';
const EDITOR = 'quarUiEditor';

const asUser = (name: string): void => {
	cy.logout();
	cy.login(name, PASSWORD);
};

/** Submits one text file to the form as a guest, then logs root back in. */
const submitAsGuest = (formId: string, text: string): void => {
	cy.logout();
	postDirectMultipartSubmission({formId, body: submissionWithText('Ada', text), headers: withSameOriginHeaders()})
		.then(expectSuccessResponse);
	cy.login();
};

const openQuarantinePage = (): void => {
	cy.visit(QUARANTINE_PAGE);
	cy.get('[data-sel-role="quarantine-app"]', {timeout: 60000}).should('exist');
};

const byRole = (role: string) => cy.get(`[data-sel-role="${role}"]`);

/**
 * The manager screen of the quarantine, a jContent route of the site (tests/scenarios/quarantine.md, Q35 to Q37). It
 * lists the held submissions of the site through the GraphQL API, opens one, and runs the operations that the API
 * allows; Release and Reject ask for a confirmation first. A user who does not manage the site gets a plain text.
 */
describe('Quarantine - 89 The manager screen in jContent', () => {
	useFormidableSite();

	let formId: string;

	before(() => {
		cy.login();
		enableModule(QUARANTINE_MODULE_ID, SITE);
		setUploadsRequireQuarantine(false);
		[MANAGER, EDITOR].forEach(name => createUser(name, PASSWORD));
		grantRoles(SITE_PATH, [QUARANTINE_MANAGER_ROLE], MANAGER, 'USER');
		grantRoles(SITE_PATH, ['editor'], EDITOR, 'USER');
		// the API checks the permission on the live site node: the grants apply once the site is published
		publishAndWaitJobEnding(SITE_PATH);

		// three inconclusive files, which the run sends to review
		createPublishedLiveFormPage('quarantine-ui-form', FORM_TITLE, fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId: id}) => {
			formId = id;
			[1, 2, 3].forEach(claim => submitAsGuest(id, `${STUB_FILE.inconclusive}\nUI claim ${claim}.`));
			runQuarantine(id).its('processed').should('have.length', 3);
		});
	});

	after(() => {
		cy.logout();
		cy.login();
		[MANAGER, EDITOR].forEach(name => deleteUser(name));
	});

	it('Q35: a manager sees the list of the site, opens a submission and releases it after the confirmation', () => {
		asUser(MANAGER);
		openQuarantinePage();
		cy.get('h1').should('contain.text', 'Quarantine');
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').should('contain.text', '3');
		byRole('quarantine-row').should('have.length', 3);
		byRole('quarantine-form-filter').find('option').should('contain.text', FORM_TITLE);

		// the state filter keeps the submissions in review, and the status region says how many
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').click();
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').should('have.attr', 'aria-pressed', 'true');
		byRole('quarantine-status').should('contain.text', '1 to 3 of 3');

		byRole('quarantine-open').first().click();
		byRole('quarantine-detail').should('exist');
		cy.focused().should('have.attr', 'id', 'fmdbq-detail-title');
		byRole('quarantine-detail').within(() => {
			cy.get('h2').should('contain.text', FORM_TITLE);
			byRole('quarantine-file').should('contain.text', 'claim-note.txt');
			// the buttons are those of allowedOperations, in the fixed order
			cy.get('[data-sel-role^="quarantine-action-"]').then($buttons => {
				expect([...$buttons].map(button => button.dataset.selRole)).to.deep.equal(
					['quarantine-action-retry', 'quarantine-action-release', 'quarantine-action-reject']);
			});
			// the names of the sensitive fields only, never a value: the form shows none
			cy.root().should('not.contain.text', 'Ada');
		});

		byRole('quarantine-action-release').click();
		byRole('quarantine-confirm-release').should('have.attr', 'open');
		byRole('quarantine-confirm-ok').click();
		byRole('quarantine-confirm-release').should('not.exist');
		// the released submission leaves the filter on review: the detail closes and the status region says so
		byRole('quarantine-status').should('contain.text', 'Submission released');
		byRole('quarantine-row').should('have.length', 2);

		cy.login();
		readHeldSubmissions(formId).then(held => {
			const released = held.find(one => one.audit.some(record => record.operation === 'release'));
			expect(released, 'the released submission').to.not.equal(undefined);
			expect(released?.audit.find(record => record.operation === 'release')?.user, 'user of the release').to.eq(MANAGER);
			expect(['releasing', 'completed'], 'state after the release').to.include(released?.state);
		});
	});

	it('Q36: an editor who does not hold the role gets the no-access text, and no list', () => {
		asUser(EDITOR);
		openQuarantinePage();
		byRole('quarantine-no-access').should('be.visible');
		byRole('quarantine-table').should('not.exist');
		byRole('quarantine-row').should('not.exist');
		cy.login();
	});

	it('Q37: the confirm dialog is reached and left with the keyboard, with the focus on Cancel first', () => {
		asUser(MANAGER);
		openQuarantinePage();
		byRole('quarantine-open').first().focus();
		cy.realPress('Enter');
		cy.focused().should('have.attr', 'id', 'fmdbq-detail-title');

		byRole('quarantine-action-reject').focus();
		cy.realPress('Enter');
		byRole('quarantine-confirm-reject').should('have.attr', 'open');
		byRole('quarantine-confirm-reject').should('have.attr', 'role', 'alertdialog');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-confirm-cancel');

		// Escape cancels: nothing ran, and the focus is back on Reject
		cy.realPress('Escape');
		byRole('quarantine-confirm-reject').should('not.exist');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-action-reject');

		// Tab from Cancel reaches the confirm button, and Enter there rejects
		cy.realPress('Enter');
		byRole('quarantine-confirm-reject').should('have.attr', 'open');
		cy.realPress('Tab');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-confirm-ok');
		cy.realPress('Enter');
		byRole('quarantine-confirm-reject').should('not.exist');
		byRole('quarantine-status').should('contain.text', 'Submission rejected');

		cy.login();
		readHeldSubmissions(formId).then(held => {
			expect(held.filter(one => one.state === 'rejected'), 'rejected submissions').to.have.length(1);
		});
	});
});
