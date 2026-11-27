import {createSite, createUser, deleteSite, deleteUser, enableModule, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {FORMIDABLE_MODULE_IDS, GRAPHQL_PATH} from '../../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	QUARANTINE_MANAGER_ROLE,
	QUARANTINE_MODULE_ID,
	createFormNode,
	createPublishedLiveFormPage,
	getRemoteStubCheckNode,
	getSaveToJcrActionNode
} from '../../support/fixtures';
import {expectSuccessResponse, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	GraphqlAnswer,
	GraphqlHeldState,
	STUB_FILE,
	fields,
	heldIdOf,
	operateHeldSubmission,
	quarantineGraphql,
	quarantineIsVisible,
	readHeldSubmissions,
	refusedByThePlatform,
	runQuarantine,
	runQuarantineRetention,
	setUploadsRequireQuarantine,
	submissionWithText
} from './support';

const SITE_A = FORMIDABLE_TEST_SITE.key;
const SITE_B = 'FormidableQuarantineSiteB';
const SITE_PATH_A = `/sites/${SITE_A}`;
const NO_SUCH_SITE = 'FormidableNoSuchSite';

/** The users of the spec: the manager holds the site role on site A alone; the editor and the site administrator of A do not hold it. */
const PASSWORD = 'Quarantine-GraphQL-90';
const MANAGER = 'quarGqlManager';
const EDITOR = 'quarGqlEditor';
const SITE_ADMIN = 'quarGqlSiteAdmin';
const USERS = [MANAGER, EDITOR, SITE_ADMIN];

const asUser = (name: string): void => {
	cy.logout();
	cy.login(name, PASSWORD);
};

const asRoot = (): void => {
	cy.logout();
	cy.login();
};

/** Submits one text file to the form as a guest, expects the body of any accepted submission, and logs root back in. */
const submitAsGuest = (formId: string, text: string): void => {
	cy.logout();
	postDirectMultipartSubmission({formId, body: submissionWithText('Ada', text), headers: withSameOriginHeaders()})
		.then(expectSuccessResponse);
	cy.login();
};

/** What the detail of a held submission answers, of the fields the spec reads. */
interface SubmissionDetail {
	id: string;
	state: GraphqlHeldState;
	formTitle: string | null;
	allowedOperations: string[];
	publicValues: Array<{name: string; values: string[]}> | null;
	audit: Array<{operation: string; user: string | null; from: string; to: string}>;
}

type SubmissionAnswer = {formidableQuarantine: {submission: SubmissionDetail | null} | null};

/** One held submission of a site, by its identifier, as the user of the session reads it. */
const readSubmission = (siteKey: string, id: string): Cypress.Chainable<Cypress.Response<GraphqlAnswer<SubmissionAnswer>>> =>
	quarantineGraphql<SubmissionAnswer>(
		`query($siteKey: String!, $id: String!) {
			formidableQuarantine(siteKey: $siteKey) {
				submission(id: $id) {id state formTitle allowedOperations publicValues {name values} audit {operation user from to}}
			}
		}`,
		{siteKey, id}
	);

/**
 * The GraphQL API of formidable-quarantine and the permission of a manager per site (tests/scenarios/quarantine.md,
 * Q32 to Q37). The query and the mutation formidableQuarantine(siteKey) check the permission formidableQuarantineManage
 * on the live site node first: a user who does not hold it gets null, the answer of a site that does not exist. Every read
 * and every operation stays below the quarantine root of that site, so an identifier of site B names nothing in the
 * quarantine of site A. Each operation writes the audit record with the name of the user of the request.
 *
 * Q32 and Q34 are control scenarios. Their control build is the one of Q20 in tests/scenarios/quarantine.md: the
 * check of the permission removed in ManagerApi.open and in QuarantineOperations.applyLocked. Build it with `package`,
 * deploy it in place of the module, and run this spec: Q32 must fail on the quarantine of site B, and Q34 on the
 * visibility for the editor.
 */
describe('Quarantine - 90 The GraphQL API answers the managers of a site alone', () => {
	useFormidableSite();

	let formIdA: string;
	let formIdB: string;
	let pathsA: string[];

	before(() => {
		cy.login();
		enableModule(QUARANTINE_MODULE_ID, SITE_A);
		setUploadsRequireQuarantine(false);

		deleteSite(SITE_B);
		createSite(SITE_B, {...FORMIDABLE_TEST_SITE.config, serverName: 'quarantineb'});
		FORMIDABLE_MODULE_IDS.forEach(moduleId => enableModule(moduleId, SITE_B));
		enableModule(QUARANTINE_MODULE_ID, SITE_B);

		USERS.forEach(name => createUser(name, PASSWORD));
		grantRoles(SITE_PATH_A, [QUARANTINE_MANAGER_ROLE], MANAGER, 'USER');
		grantRoles(SITE_PATH_A, ['editor'], EDITOR, 'USER');
		grantRoles(SITE_PATH_A, ['site-administrator'], SITE_ADMIN, 'USER');
		// the API checks the permission on the live site node: the grants apply once the site is published
		publishAndWaitJobEnding(SITE_PATH_A);

		// site A: two inconclusive files, which the run sends to review
		createPublishedLiveFormPage('quarantine-graphql-form', 'Quarantine GraphQL form', fields(), undefined, undefined, {
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(({formId}) => {
			formIdA = formId;
			[1, 2].forEach(claim => submitAsGuest(formId, `${STUB_FILE.inconclusive}\nGraphQL claim ${claim}.`));
			runQuarantine(formId).its('processed').should('have.length', 2);
			readHeldSubmissions(formId).then(held => {
				expect(held.map(one => one.state), 'states on site A').to.deep.equal(['needsReview', 'needsReview']);
				pathsA = held.map(one => one.path);
			});
		});

		// site B: one held submission, which no run touches
		const formPathB = `/sites/${SITE_B}/contents/quarantine-graphql-form-b`;
		createFormNode('quarantine-graphql-form-b', 'Quarantine GraphQL form B', fields(), {
			parentPath: `/sites/${SITE_B}/contents`,
			qualifiers: [getRemoteStubCheckNode()],
			deferredActions: [getSaveToJcrActionNode()]
		}).then(response => {
			formIdB = response?.data?.jcr?.addNode?.uuid;
			publishAndWaitJobEnding(formPathB);
			submitAsGuest(formIdB, `${STUB_FILE.inconclusive}\nA claim of site B.`);
			readHeldSubmissions(formIdB, SITE_B).its('length').should('eq', 1);
		});
	});

	after(() => {
		cy.logout();
		cy.login();
		USERS.forEach(name => deleteUser(name));
		deleteSite(SITE_B);
	});

	it('Q32: a manager of site A reads the quarantine of A, and site B answers as a site that does not exist (control)', () => {
		asUser(MANAGER);
		quarantineIsVisible(SITE_A).should('eq', true);
		readHeldSubmissions(formIdA).then(held => {
			expect(held.map(one => one.path), 'held submissions of site A').to.deep.equal(pathsA);
		});
		quarantineGraphql<{formidableQuarantine: {counts: Array<{state: string; count: number}>} | null}>(
			'query($siteKey: String!) { formidableQuarantine(siteKey: $siteKey) { counts { state count } } }',
			{siteKey: SITE_A}
		).then(response => {
			const counts = response.body.data?.formidableQuarantine?.counts ?? [];
			expect(counts.find(count => count.state === 'NEEDS_REVIEW')?.count, 'submissions in review').to.eq(2);
		});

		const siteQuery = 'query($siteKey: String!) { formidableQuarantine(siteKey: $siteKey) { siteKey counts { state count } } }';
		quarantineGraphql(siteQuery, {siteKey: NO_SUCH_SITE}).then(missing => {
			quarantineGraphql(siteQuery, {siteKey: SITE_B}).then(siteB => {
				expect(siteB.body, 'the answer of site B, as of a site that does not exist').to.deep.equal(missing.body);
				expect(siteB.body.data, 'the data of site B').to.deep.equal({formidableQuarantine: null});
			});
		});
		asRoot();
	});

	it('Q33: an identifier of site B names nothing in the quarantine of site A, and the operation changes nothing', () => {
		readHeldSubmissions(formIdB, SITE_B).then(([heldB]) => {
			const idB = heldIdOf(heldB.path, SITE_B);
			asUser(MANAGER);
			readSubmission(SITE_A, idB).its('body.data.formidableQuarantine.submission').should('eq', null);
			readSubmission(SITE_A, `../../${SITE_B}/formidable-quarantine/${idB}`)
				.its('body.data.formidableQuarantine.submission').should('eq', null);
			quarantineGraphql<{formidableQuarantine: unknown}>(
				`mutation($siteKey: String!, $id: String!) {
					formidableQuarantine(siteKey: $siteKey) { operate(id: $id, operation: REJECT) { id } }
				}`,
				{siteKey: SITE_A, id: idB}
			).then(response => {
				expect(response.body.data?.formidableQuarantine ?? null, 'operation on an identifier of site B').to.eq(null);
				expect(JSON.stringify(response.body.errors ?? []), 'answer of the operation').to.contain('No held submission');
			});
			asRoot();
			readHeldSubmissions(formIdB, SITE_B).then(([after]) => {
				expect(after.state, 'state of the submission of site B').to.eq('held');
				expect(after.audit, 'audit of the submission of site B').to.deep.equal([]);
				expect(after.valuesPresent, 'values of the submission of site B').to.eq(true);
			});
		});
	});

	[EDITOR, SITE_ADMIN].forEach(name => {
		it(`Q34: ${name} does not hold the permission, sees nothing and changes nothing (control)`, () => {
			asUser(name);
			quarantineIsVisible(SITE_A).should('eq', false);
			pathsA.forEach(path => operateHeldSubmission(path, 'reject', false).its('status').should('eq', 403));
			runQuarantineRetention(0, false).its('status').should('eq', 403);
			asRoot();
			readHeldSubmissions(formIdA).then(held => {
				expect(held.map(one => one.state), 'states').to.deep.equal(['needsReview', 'needsReview']);
				held.forEach(one => expect(one.audit.map(record => record.operation), 'audit').to.deep.equal(['run']));
			});
		});
	});

	it('Q35: the manager rejects a submission, the audit names the manager, and the retention stays with the server administrators', () => {
		const id = heldIdOf(pathsA[0]);
		asUser(MANAGER);
		readSubmission(SITE_A, id).then(response => {
			const held = response.body.data?.formidableQuarantine?.submission;
			expect(held?.state, 'state').to.eq('NEEDS_REVIEW');
			expect(held?.formTitle, 'title of the form').to.eq('Quarantine GraphQL form');
			expect(held?.allowedOperations, 'operations of a submission in review').to.deep.equal(['RETRY', 'RELEASE', 'REJECT']);
			expect(held?.publicValues, 'values of a form that does not show them').to.eq(null);
			// the query selects the values: a leak would put the value of the submission in the raw answer
			expect(JSON.stringify(response.body), 'raw answer of the API').to.not.contain('Ada');
		});
		operateHeldSubmission(pathsA[0], 'reject').then(response => {
			expect(response.body.state, 'state after the reject').to.eq('rejected');
			expect(response.body.from, 'state before the reject').to.eq('needsReview');
		});
		readSubmission(SITE_A, id).its('body.data.formidableQuarantine.submission.allowedOperations').should('deep.equal', []);
		operateHeldSubmission(pathsA[0], 'release', false).its('status').should('eq', 409);
		runQuarantineRetention(0, false).its('status').should('eq', 403);

		asRoot();
		readHeldSubmissions(formIdA).then(held => {
			const audit = held[0].audit.map(record => [record.operation, record.user, record.from, record.to]);
			expect(audit, 'audit of the rejected submission').to.deep.equal([
				// one run took the submission from held to review: the record names the state before that run
				['run', null, 'held', 'needsReview'],
				['reject', MANAGER, 'needsReview', 'rejected']
			]);
			expect(held[0].valuesPresent, 'values of the rejected submission').to.eq(false);
		});
	});

	it('Q37: the mutation operate in a shape that a cross-site page can forge is refused, and the same from this server runs', () => {
		const id = heldIdOf(pathsA[1]);
		const mutation = `mutation { formidableQuarantine(siteKey: "${SITE_A}") { operate(id: "${id}", operation: REJECT) { id } } }`;
		/**
		 * The refusal of a forged shape: a 4xx status of the platform (401 or 403 above all), or the refusal of the API
		 * security filter (GqlAccessDeniedException); never an answer of the operation.
		 */
		const expectRefused = (shape: string) => (response: Cypress.Response<GraphqlAnswer<{formidableQuarantine: {operate: unknown} | null}>>) => {
			const body = typeof response.body === 'object' ? response.body : undefined;
			const refused = (response.status >= 400 && response.status < 500) || refusedByThePlatform(body);
			expect(refused, `refusal of the ${shape}: ${response.status} ${JSON.stringify(response.body)}`).to.eq(true);
			expect(body?.data?.formidableQuarantine?.operate ?? null, `operation of the ${shape}`).to.eq(null);
		};
		asUser(MANAGER);
		// a simple request that a cross-site form sends with no preflight: text/plain, then form-urlencoded, no Origin
		cy.request({
			method: 'POST', url: GRAPHQL_PATH, failOnStatusCode: false,
			headers: {'Content-Type': 'text/plain'}, body: JSON.stringify({query: mutation})
		}).then(expectRefused('text/plain POST'));
		cy.request({
			method: 'POST', url: GRAPHQL_PATH, failOnStatusCode: false, form: true, body: {query: mutation}
		}).then(expectRefused('form-urlencoded POST'));
		// a JSON request that a browser marks as cross-site
		cy.request({
			method: 'POST', url: GRAPHQL_PATH, failOnStatusCode: false,
			headers: {'Content-Type': 'application/json', 'Sec-Fetch-Site': 'cross-site', Origin: 'https://attacker.example'},
			body: {query: mutation}
		}).then(expectRefused('cross-site JSON POST'));
		asRoot();
		readHeldSubmissions(formIdA).then(held => {
			const target = held.find(one => one.path === pathsA[1]);
			expect(target?.state, 'state of the target').to.eq('needsReview');
			expect(target?.audit.map(record => record.operation), 'audit of the target').to.deep.equal(['run']);
		});

		// the control: the same mutation, as a JSON POST from a page of this server, runs
		asUser(MANAGER);
		quarantineGraphql<{formidableQuarantine: {operate: {id: string}} | null}>(mutation, {}).then(response => {
			expect(response.body.errors ?? [], 'errors of the same-origin mutation').to.deep.equal([]);
			expect(response.body.data?.formidableQuarantine?.operate?.id, 'the operation ran').to.eq(id);
		});
		asRoot();
		readHeldSubmissions(formIdA).then(held => {
			const target = held.find(one => one.path === pathsA[1]);
			expect(target?.state, 'state of the target after the control').to.eq('rejected');
			expect(target?.audit.map(record => [record.operation, record.user]), 'audit after the control')
				.to.deep.equal([['run', null], ['reject', MANAGER]]);
		});
	});

	it('Q36: a page holds at most 100 submissions, whatever limit the request asks for', () => {
		quarantineGraphql<{formidableQuarantine: {submissions: {limit: number; offset: number; totalCount: number; items: unknown[]}} | null}>(
			`query($siteKey: String!) {
				formidableQuarantine(siteKey: $siteKey) {
					submissions(limit: 1000, offset: -5) { limit offset totalCount items { id } }
				}
			}`,
			{siteKey: SITE_A}
		).then(response => {
			const page = response.body.data?.formidableQuarantine?.submissions;
			expect(page?.limit, 'limit applied').to.eq(100);
			expect(page?.offset, 'offset applied').to.eq(0);
			expect(page?.items.length, 'items').to.eq(page?.totalCount);
		});
	});
});
