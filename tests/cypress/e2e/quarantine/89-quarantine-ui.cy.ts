import {createUser, deleteUser, enableModule, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	FORMIDABLE_TEST_SITE,
	QUARANTINE_MANAGER_ROLE,
	QUARANTINE_MODULE_ID,
	createPublishedLiveFormPage,
	getPasswordNode,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	STUB_FILE,
	fields,
	readHeldSubmissions,
	runQuarantine,
	setUploadsRequireQuarantine,
	submissionWithText,
	submissionWithValues
} from './support';

const SITE = FORMIDABLE_TEST_SITE.key;
const SITE_PATH = `/sites/${SITE}`;
const QUARANTINE_PAGE = `/jahia/jcontent/${SITE}/en/apps/formidableQuarantine`;
const FORM_TITLE = 'Quarantine UI form';
/** A form with one sensitive field, whose name the detail shows and whose value never reaches the screen. */
const SENSITIVE_FORM_TITLE = 'Quarantine UI sensitive form';
const SENSITIVE_FIELD = 'accessCode';
const SENSITIVE_VALUE = 'never-on-screen-89';

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

/** Leaves jContent before root logs in again: a switch of user under an open Jahia UI can keep its cookie. */
const backToRoot = (): void => {
	cy.visit('/cms/logout', {failOnStatusCode: false});
	cy.login();
};

/**
 * The manager screen of the quarantine, a jContent route of the site (tests/scenarios/quarantine.md, Q35 to Q38). It
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
		// the h1 of the Moonstone Header of the screen: jContent may render one of its own elsewhere
		byRole('quarantine-app').find('h1').should('contain.text', 'Quarantine');
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').should('contain.text', '3');
		byRole('quarantine-row').should('have.length', 3);
		// the form filter: a chip that opens a dialog of radios, and Escape closes it on the chip
		byRole('quarantine-form-filter').click();
		byRole('quarantine-form-editor').should('contain.text', FORM_TITLE).find('input[type="radio"]').should('have.length', 2);
		cy.realPress('Escape');
		byRole('quarantine-form-editor').should('not.exist');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-form-filter');

		// the state filter keeps the submissions in review, and the status region says how many
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').click();
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').should('have.attr', 'aria-pressed', 'true');
		// the sentence of the filtered page: the one of the first load has no state
		byRole('quarantine-status').should('contain.text', '1 to 3 of 3 submissions, state Needs review');

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
			// a form that does not show its values: no value section
			byRole('quarantine-values').should('not.exist');
		});

		byRole('quarantine-action-release').click();
		byRole('quarantine-confirm-release').should('be.visible');
		byRole('quarantine-confirm-ok').click();
		byRole('quarantine-confirm-release').should('not.exist');
		// the released submission leaves the filter on review: the detail closes and the status region says so
		byRole('quarantine-status').should('contain.text', 'Submission released');
		byRole('quarantine-row').should('have.length', 2);

		backToRoot();
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
	});

	it('Q37: the confirm dialog is reached and left with the keyboard, with the focus on Cancel first', () => {
		asUser(MANAGER);
		openQuarantinePage();
		// the filter keeps the submissions that a manager can still reject, however many an earlier test left
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').click();
		byRole('quarantine-state-filter').find('button[data-state="NEEDS_REVIEW"]').should('have.attr', 'aria-pressed', 'true');
		byRole('quarantine-row').should('have.length.at.least', 1);
		byRole('quarantine-open').first().focus();
		cy.realPress('Enter');
		cy.focused().should('have.attr', 'id', 'fmdbq-detail-title');

		byRole('quarantine-action-reject').focus();
		cy.realPress('Enter');
		byRole('quarantine-confirm-reject').should('be.visible');
		byRole('quarantine-confirm-reject').should('have.attr', 'role', 'alertdialog');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-confirm-cancel');

		// Escape cancels: nothing ran, and the focus is back on Reject
		cy.realPress('Escape');
		byRole('quarantine-confirm-reject').should('not.exist');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-action-reject');

		// Tab from Cancel reaches the confirm button, and Enter there rejects
		cy.realPress('Enter');
		byRole('quarantine-confirm-reject').should('be.visible');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-confirm-cancel');
		cy.realPress('Tab');
		cy.focused().should('have.attr', 'data-sel-role', 'quarantine-confirm-ok');
		cy.realPress('Enter');
		byRole('quarantine-confirm-reject').should('not.exist');
		byRole('quarantine-status').should('contain.text', 'Submission rejected');

		backToRoot();
		readHeldSubmissions(formId).then(held => {
			expect(held.filter(one => one.state === 'rejected'), 'rejected submissions').to.have.length(1);
		});
	});

	it('Q38: the detail names the sensitive fields of a submission, and never shows a sensitive value', () => {
		cy.login();
		// the form shows no value (no form declares showValues before the policy of the form): the vitest of
		// SubmissionDetail covers the values of a form that shows them
		createPublishedLiveFormPage('quarantine-ui-sensitive-form', SENSITIVE_FORM_TITLE,
			[...fields(), getPasswordNode({name: SENSITIVE_FIELD, title: 'Access code'})], undefined, undefined, {
				qualifiers: [getRemoteStubCheckNode()],
				deferredActions: [getSaveToJcrActionNode()]
			}).then(({formId: id}) => {
			cy.logout();
			postDirectMultipartSubmission({
				formId: id,
				body: submissionWithValues({fullName: 'Ada', [SENSITIVE_FIELD]: SENSITIVE_VALUE}, `${STUB_FILE.inconclusive}\nUI values.`),
				headers: withSameOriginHeaders()
			}).then(expectSuccessResponse);
			cy.login();
			runQuarantine(id).its('processed').should('have.length', 1);
		});

		asUser(MANAGER);
		openQuarantinePage();
		// the form filter keeps the submission of that form alone
		byRole('quarantine-form-filter').click();
		cy.contains('[data-sel-role="quarantine-form-editor"] label', SENSITIVE_FORM_TITLE).click();
		// the editor of the filter stays open for another choice: Escape closes it
		cy.realPress('Escape');
		byRole('quarantine-form-editor').should('not.exist');
		byRole('quarantine-row').should('have.length', 1);
		byRole('quarantine-open').first().click();
		byRole('quarantine-detail').within(() => {
			byRole('quarantine-sensitive').should('contain.text', SENSITIVE_FIELD);
			byRole('quarantine-values').should('not.exist');
			cy.root().should('not.contain.text', SENSITIVE_VALUE);
		});
		backToRoot();
	});
});
