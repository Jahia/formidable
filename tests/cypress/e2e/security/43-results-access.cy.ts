import {createUser, deleteUser, grantRoles, publishAndWaitJobEnding, revokeRoles} from '@jahia/cypress';
import {
	createPublishedLiveFormPage,
	FORMIDABLE_TEST_SITE,
	getInputTextNode,
	getLatestLiveFormSubmission
} from '../../support/fixtures';
import {postDirectMultipartSubmission, useFormidableSite, withSameOriginHeaders} from './support';

const SAVE_TO_JCR_ACTION = {
	name: 'storeSubmission',
	primaryNodeType: 'fmdb:save2jcrAction',
	properties: [] as Array<{name: string; value: string}>
};

/**
 * Access to stored form results is governed by the fmdb-results-reader role: granted on
 * the form node, replicated onto the fmdb:formResults node by the ACL sync (triggered by
 * live-workspace publication events), with inheritance broken there so no site-level
 * read role leaks onto submissions. These tests exercise that chain end to end with a
 * real non-privileged user reading over GraphQL.
 */
describe('Security - 43 Form results access', () => {
	useFormidableSite();

	const READER = {name: 'formUser', password: 'FormUser#1234'};
	const FORM_NAME = 'results-access-form';

	let formPath: string;
	let submissionPath: string;

	/** Swaps the runner session for the reader's: every subsequent request runs as them. */
	const loginAsReader = () => {
		cy.logout();
		cy.login(READER.name, READER.password);
	};

	/**
	 * GraphQL read of a live node with the CURRENT session (log in as the intended user
	 * first). Session auth rather than basic auth: the CI image gates basic auth on
	 * /modules/graphql, and a session cookie is what the real results screen uses anyway.
	 */
	const readNodeAs = (expectedUser: string, path: string) =>
		cy.request({
			method: 'POST',
			url: '/modules/graphql',
			failOnStatusCode: false,
			headers: withSameOriginHeaders(),
			body: {
				query: `query ReadAsUser($path: String!) {
					currentUser {name}
					jcr(workspace: LIVE) {
						nodeByPath(path: $path) {
							name
							fullName: property(name: "fullName") {value}
						}
					}
				}`,
				variables: {path}
			}
		}).then(response => {
			const body = response.body as {
				errors?: Array<{message?: string}>;
				data?: {
					currentUser?: {name: string};
					jcr?: {nodeByPath?: {name: string; fullName?: {value: string} | null} | null} | null;
				};
			};
			// The whole point of these tests is WHO reads: fail loudly if the request
			// was not authenticated as the intended user (e.g. a leaked session cookie).
			expect(body.data?.currentUser?.name, 'authenticated user for the read').to.eq(expectedUser);
			// A denied read comes back as nodeByPath:null WITH a PathNotFoundException
			// for the queried path — that pair IS the expected deny signal. Any other
			// GraphQL error would make a null indistinguishable from a deny, so it
			// fails loudly instead of producing a false verdict.
			const unexpectedErrors = (body.errors ?? [])
				.filter(error => !(error.message ?? '').includes(`PathNotFoundException: ${path}`));
			expect(unexpectedErrors, 'unexpected GraphQL errors').to.deep.eq([]);
			return cy.wrap(body.data?.jcr?.nodeByPath ?? null, {log: false});
		});

	before(() => {
		cy.login();
		createUser(READER.name, READER.password);
		// A results reader is a back-office user: editor on the site makes the account
		// privileged, which is what the platform requires to call the GraphQL API at
		// all (the real results screen lives in jContent under the same requirement).
		// It grants NOTHING on stored results: their ACL inheritance is broken, so the
		// deny assertions below hold with it — which is exactly the point.
		grantRoles(`/sites/${FORMIDABLE_TEST_SITE.key}`, ['editor'], READER.name, 'USER');

		createPublishedLiveFormPage(
			FORM_NAME,
			'Results access form',
			[getInputTextNode({name: 'fullName', title: 'Full name'})],
			undefined,
			undefined,
			{actions: [SAVE_TO_JCR_ACTION]}
		).then(info => {
			formPath = info.formPath;

			cy.logout();
			postDirectMultipartSubmission({
				formId: info.formId,
				fields: {fullName: 'Confidential Person'},
				headers: withSameOriginHeaders()
			}).its('status').should('eq', 200);

			cy.login();
			getLatestLiveFormSubmission(FORM_NAME).then(({path}) => {
				submissionPath = `${path}/data`;
			});
		});
	});

	after(() => {
		cy.login();
		deleteUser(READER.name);
	});

	it('denies a user without the results-reader role', () => {
		// The user authenticates fine but must see nothing of the stored results: neither
		// the submission data nor the per-form results node, whose broken ACL inheritance
		// is the actual lock. The formidable-results root itself deliberately stays
		// readable — it inherits the site ACL and carries nothing but the names of forms
		// having results.
		loginAsReader();
		readNodeAs(READER.name, submissionPath).should('be.null');
		readNodeAs(READER.name, `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${FORM_NAME}`)
			.should('be.null');

		// Pin the boundary from the other side too: the root stays readable, the lock
		// really is the per-form node right below it.
		readNodeAs(READER.name, `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results`)
			.should('not.be.null');
	});

	it('grants access once fmdb-results-reader is given on the form and published', () => {
		cy.login();
		grantRoles(formPath, ['fmdb-results-reader'], READER.name, 'USER');
		publishAndWaitJobEnding(formPath);

		// The ACE reaches live with the publication, then the listener replicates it
		// onto the formResults node asynchronously — hence the retry.
		loginAsReader();
		cy.waitUntil(
			() => readNodeAs(READER.name, submissionPath).then(node => node !== null),
			{timeout: 15000, interval: 500, errorMsg: 'submission never became readable after the grant'}
		);

		readNodeAs(READER.name, submissionPath).then(node => {
			expect(node?.name).to.eq('data');
			expect(node?.fullName?.value).to.eq('Confidential Person');
		});
	});

	it('denies the user again once the role is revoked and republished', () => {
		cy.login();
		revokeRoles(formPath, ['fmdb-results-reader'], READER.name, 'USER');
		publishAndWaitJobEnding(formPath);

		// The ACE removal must propagate to the formResults node the same way the
		// grant did: the sync removes replicated entries no longer present on the form.
		loginAsReader();
		cy.waitUntil(
			() => readNodeAs(READER.name, submissionPath).then(node => node === null),
			{timeout: 15000, interval: 500, errorMsg: 'submission stayed readable after the revoke'}
		);
	});
});
