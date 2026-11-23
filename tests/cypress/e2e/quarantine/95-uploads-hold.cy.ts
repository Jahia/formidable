import {disableModule, enableModule} from '@jahia/cypress';
import {CONTENT_PATH} from '../../support/constants';
import {
	FORMIDABLE_TEST_SITE,
	FORWARD_SINK_TARGET_ID,
	createPublishedLiveFormPage,
	getForwardActionNode,
	getLatestLiveFormSubmission,
	getLatestLiveSubmissionValues,
	getSaveToJcrActionNode,
	getUploadObserverActionNode,
	visitEditForm,
	visitLiveForm
} from '../../support/fixtures';
import {SAME_ORIGIN, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	FILE_FIELD,
	FILE_NAME,
	FORWARD_SINK_TARGET,
	fields,
	readForwardSink,
	readUploadObservations,
	resetForwardSink,
	resetUploadObservations,
	setDevelopmentForwardTargets,
	setForwardTargets,
	setUploadsRequireQuarantine,
	submissionWithAFile,
	submissionWithNoFile
} from './support';

const SAMPLES_MODULE = 'formidable-test-module-samples-java';
/** The samples' check type: the one type of the repository that the "Quarantine checks" list accepts. */
const SAMPLE_CHECK = 'fmdbsample:sampleCheck';

/** The body of a GraphQL answer: the data of the query, or the errors that refused it. */
interface GraphqlBody {
	// eslint-disable-next-line @typescript-eslint/no-explicit-any -- each query of the spec reads its own shape
	data: any;
	errors?: Array<{message: string}>;
}

const graphql = (query: string, variables: Record<string, unknown>): Cypress.Chainable<Cypress.Response<GraphqlBody>> =>
	cy.request<GraphqlBody>({
		method: 'POST',
		url: '/modules/graphql',
		headers: {Origin: SAME_ORIGIN},
		failOnStatusCode: false,
		body: {query, variables}
	});

/** The names of the files Save to JCR stored under the latest live submission of the form. */
const storedFileNames = (formName: string): Cypress.Chainable<string[]> =>
	getLatestLiveFormSubmission(formName).then(({path}) => graphql(
		'query($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { descendants(typesFilter: {types: ["jnt:file"]}) { nodes { name } } } } }',
		{path}
	)).then(response => {
		const nodes = response.body.data.jcr.nodeByPath.descendants.nodes as Array<{name: string}>;
		return nodes.map(node => node.name);
	});

/** The node types that a list accepts under it, as the editor asks the repository for its create button. */
const typesOfferedUnder = (path: string): Cypress.Chainable<string[]> => graphql(
	'query($path: String!) { jcr { nodeByPath(path: $path) { allowedChildNodeTypes(includeSubTypes: true) { name } } } }',
	{path}
).then(response => {
	expect(response.body.errors, 'errors of the query').to.equal(undefined);
	const types = response.body.data.jcr.nodeByPath.allowedChildNodeTypes as Array<{name: string}>;
	return types.map(type => type.name);
});

const primaryTypesUnder = (path: string): Cypress.Chainable<string[]> => graphql(
	'query($path: String!) { jcr { nodeByPath(path: $path) { children { nodes { primaryNodeType { name } } } } } }',
	{path}
).then(response => {
	const nodes = response.body.data.jcr.nodeByPath.children.nodes as Array<{primaryNodeType: {name: string}}>;
	return nodes.map(node => node.primaryNodeType.name);
});

/**
 * The hold of the uploads of a form (docs/adr/0002, tests/scenarios/quarantine.md): with the switch "Hold uploads in
 * quarantine" on, no action on submission receives a file, and the actions after release receive the released files.
 * The samples module ships the observables: a hold that releases a submission inside the request, an observer action,
 * which both record the number of files they received, and the receiver of a forward target, which answers the names
 * of the parts the last forward carried.
 */
describe('Quarantine - 95 A form with the switch on holds its uploads until release', () => {
	useFormidableSite();

	before(() => {
		setForwardTargets([FORWARD_SINK_TARGET]);
		setDevelopmentForwardTargets(true);
		setUploadsRequireQuarantine(false);
	});

	beforeEach(() => {
		resetForwardSink();
		resetUploadObservations();
	});

	after(() => {
		setUploadsRequireQuarantine(false);
		setDevelopmentForwardTargets(false);
		setForwardTargets([]);
	});

	it('Q07: with the switch on, the forward action on submission sends no file', () => {
		createPublishedLiveFormPage('held-forward-form', 'Held forward form', fields(), undefined, undefined, {
			actions: [getForwardActionNode({targetId: FORWARD_SINK_TARGET_ID})],
			deferredActions: []
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Ada'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		readForwardSink().then(forwarded => {
			expect(forwarded.count, 'forwards received').to.eq(1);
			expect(forwarded.fields, 'value parts forwarded').to.include('fullName');
			expect(forwarded.files, 'file parts forwarded').to.deep.equal([]);
		});
	});

	it('Q08: with the switch on, a third-party action on submission receives no file, and the hold receives it', () => {
		createPublishedLiveFormPage('held-observer-form', 'Held observer form', fields(), undefined, undefined, {
			actions: [getUploadObserverActionNode('onSubmission')],
			deferredActions: [getUploadObserverActionNode('afterRelease')]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Ada'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		// The action on submission reports no file, and the sample hold reports the one file. The same action placed
		// after release received the file that the hold released.
		readUploadObservations().then(seen => {
			expect(seen.observer, 'calls of the observer').to.deep.equal([
				{action: 'onSubmission', list: 'actions', files: 0},
				{action: 'afterRelease', list: 'deferredActions', files: 1}
			]);
			expect(seen.hold, 'files of each call of the hold').to.deep.equal([1]);
		});
	});

	it('Q14: a form with the switch off hands its file to every action, as before', () => {
		const formName = 'plain-upload-form';
		createPublishedLiveFormPage(formName, 'Plain upload form', fields(), undefined, undefined, {
			actions: [
				getSaveToJcrActionNode(),
				getForwardActionNode({targetId: FORWARD_SINK_TARGET_ID}),
				getUploadObserverActionNode()
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Grace'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		readForwardSink().then(forwarded => {
			expect(forwarded.count, 'forwards received').to.eq(1);
			expect(forwarded.files, 'file parts forwarded').to.deep.equal([FILE_FIELD]);
		});
		readUploadObservations().then(seen => {
			expect(seen.observer, 'calls of the observer').to.deep.equal([{action: 'uploadObserver', list: 'actions', files: 1}]);
			expect(seen.hold, 'calls of the hold').to.deep.equal([]);
		});

		cy.login();
		getLatestLiveSubmissionValues(formName).its('fullName').should('eq', 'Grace');
		storedFileNames(formName).should('deep.equal', [FILE_NAME]);
	});

	it('Q15: with a mandatory quarantine, a form with the switch off keeps its values and hands its file to no action', () => {
		const formName = 'mandatory-quarantine-form';
		setUploadsRequireQuarantine(true);
		createPublishedLiveFormPage(formName, 'Mandatory quarantine form', fields(), undefined, undefined, {
			actions: [
				getSaveToJcrActionNode(),
				getForwardActionNode({targetId: FORWARD_SINK_TARGET_ID}),
				getUploadObserverActionNode()
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Hedy'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		readForwardSink().then(forwarded => {
			expect(forwarded.count, 'forwards received').to.eq(1);
			expect(forwarded.fields, 'value parts forwarded').to.include('fullName');
			expect(forwarded.files, 'file parts forwarded').to.deep.equal([]);
		});
		readUploadObservations().its('observer').should('deep.equal', [{action: 'uploadObserver', list: 'actions', files: 0}]);

		cy.login();
		getLatestLiveSubmissionValues(formName).its('fullName').should('eq', 'Hedy');
		storedFileNames(formName).should('deep.equal', []);
		setUploadsRequireQuarantine(false);
	});

	it('Q25: with the switch on and no file, the actions after release ran before the response', () => {
		const formName = 'held-form-no-file';
		createPublishedLiveFormPage(formName, 'Held form without a file', fields(), undefined, undefined, {
			deferredActions: [getSaveToJcrActionNode(), getUploadObserverActionNode('afterRelease')]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithNoFile('Katherine'), headers: withSameOriginHeaders()})
				.its('status').should('eq', 200);
		});

		// Read with no wait: the response arrived, so the two actions have run.
		readUploadObservations().then(seen => {
			expect(seen.observer, 'calls of the observer').to.deep.equal([{action: 'afterRelease', list: 'deferredActions', files: 0}]);
			expect(seen.hold, 'calls of the hold').to.deep.equal([]);
		});
		cy.login();
		getLatestLiveSubmissionValues(formName).its('fullName').should('eq', 'Katherine');
	});

	it('the editor of a form offers the switch, off on a plain form and on once the form holds its uploads', () => {
		const editorSwitch = (path: string): Cypress.Chainable<{dynamic: boolean; activated: boolean; displayName: string} | undefined> => graphql(
			'query($path: String!) { forms { editForm(uuidOrPath: $path, uiLocale: "en", locale: "en") { sections { fieldSets { name dynamic activated displayName } } } } }',
			{path}
		).then(response => {
			expect(response.body.errors, 'errors of the query').to.equal(undefined);
			const sections = response.body.data.forms.editForm.sections as Array<{fieldSets: Array<{name: string; dynamic: boolean; activated: boolean; displayName: string}>}>;
			return sections.flatMap(section => section.fieldSets).find(fieldSet => fieldSet.name === 'fmdbmix:quarantine');
		});
		createPublishedLiveFormPage('switch-off-form', 'Switch off form', fields());
		createPublishedLiveFormPage('switch-on-form', 'Switch on form', fields(), undefined, undefined, {deferredActions: []});

		editorSwitch(`${CONTENT_PATH}/switch-off-form`).should('deep.equal',
			{name: 'fmdbmix:quarantine', dynamic: true, activated: false, displayName: 'Hold uploads in quarantine'});
		editorSwitch(`${CONTENT_PATH}/switch-on-form`).should('deep.equal',
			{name: 'fmdbmix:quarantine', dynamic: true, activated: true, displayName: 'Hold uploads in quarantine'});
	});

	it('the Page Builder shows the checks between the two action zones, with their create button', () => {
		createPublishedLiveFormPage('checks-zone-form', 'Checks zone form', fields(), undefined, undefined, {
			deferredActions: [],
			qualifiers: [{name: 'virusScan', primaryNodeType: SAMPLE_CHECK, properties: [{name: 'jcr:title', value: 'Virus scan', language: 'en'}]}]
		}).then(({pagePath, livePath, formPath}) => {
			visitEditForm(pagePath);
			// the order in which the lists run: on submission, the checks, after release
			cy.get('.fmdb-authoring-actions').should('have.length', 3).then($zones => {
				expect($zones.eq(1).hasClass('fmdb-authoring-checks'), 'second zone: the checks').to.equal(true);
				expect($zones.eq(2).hasClass('fmdb-authoring-deferred-actions'), 'third zone: after release').to.equal(true);
			});
			cy.get('.fmdb-authoring-checks').within(() => {
				cy.get('.fmdb-authoring-action').should('have.length', 1)
					.should('have.attr', 'data-fmdb-action-type', SAMPLE_CHECK)
					.find('.fmdb-authoring-action-title').should('have.text', 'Virus scan');
				cy.get('.fmdb-authoring-actions-empty').should('not.exist');
			});
			// the list's own module declares the accepted type, which jContent turns into its create button
			cy.get('[jahiatype="module"][path$="/qualifiers"]')
				.should('have.length', 1)
				.should('have.attr', 'nodetypes', 'fmdbmix:qualifier')
				.find('[jahiatype="module"][type="placeholder"]')
				.should('have.length', 1);

			// the views guard themselves: asked for directly in live or in preview, as fragments, they render nothing
			[
				{mode: 'live', base: '/cms/render/live/en'},
				{mode: 'preview', base: '/cms/preview/default/en'}
			].forEach(({mode, base}) => {
				[`${formPath}/qualifiers`, `${formPath}/qualifiers/virusScan`].forEach(path => {
					cy.request(`${base}${path}.hidden.authoring.html.ajax`).then(response => {
						expect(response.status, `${mode} render of ${path}`).to.equal(200);
						expect(response.body, `${mode} body of ${path}`).not.to.contain('fmdb-authoring-action');
					});
				});
			});

			visitLiveForm(livePath);
			cy.get('.fmdb-authoring-checks').should('not.exist');

			// a site where no module provides a check type: a call-out in place of a button that would open on nothing
			disableModule(SAMPLES_MODULE, FORMIDABLE_TEST_SITE.key);
			visitEditForm(pagePath);
			cy.get('.fmdb-authoring-checks').within(() => {
				cy.get('.fmdb-authoring-actions-unavailable').should('be.visible')
					.and('have.attr', 'data-fmdb-authoring-state', 'no-type');
				cy.get('[jahiatype="module"][type="placeholder"]').should('not.exist');
			});
			enableModule(SAMPLES_MODULE, FORMIDABLE_TEST_SITE.key);
		});

		// the create buttons are named after these labels: without one, jContent shows the bare mixin name
		graphql(
			'{ jcr { nodeTypes(filter: {includeTypes: ["fmdbmix:qualifier", "fmdbmix:deferrableAction"]}) { nodes { name displayName(language: "en") } } } }',
			{}
		).then(response => {
			expect(response.body.errors, 'errors of the query').to.equal(undefined);
			// the filter answers the subtypes too (each action and check type): keep the two mixins
			const labels = Object.fromEntries((response.body.data.jcr.nodeTypes.nodes as Array<{name: string; displayName: string}>)
				.filter(type => type.name.startsWith('fmdbmix:'))
				.map(type => [type.name, type.displayName]));
			expect(labels, 'labels of the two mixins').to.deep.equal({
				'fmdbmix:qualifier': 'Quarantine check',
				'fmdbmix:deferrableAction': 'Action after release'
			});
		});
	});

	it('Q26: a type that is not deferrable stays out of the actions after release', () => {
		const formName = 'held-form-types';
		const listPath = `${CONTENT_PATH}/${formName}/deferredActions`;
		createPublishedLiveFormPage(formName, 'Held form types', fields(), undefined, undefined, {deferredActions: []});

		typesOfferedUnder(listPath).then(offered => {
			expect(offered, 'types offered by "Actions after release"').to.include.members([
				'fmdb:save2jcrAction',
				'fmdb:emailNotificationAction',
				'fmdb:emailContentAction',
				'fmdb:forwardAction'
			]);
			expect(offered, 'types offered by "Actions after release"').to.not.include('fmdb:redirectAction');
		});

		const addAction = 'mutation($parent: String!, $name: String!, $type: String!) { jcr { addNode(parentPathOrId: $parent, name: $name, primaryNodeType: $type) { uuid } } }';
		// The same mutation creates a deferrable action, so the refusal below is the one of the type.
		graphql(addAction, {parent: listPath, name: 'store', type: 'fmdb:save2jcrAction'}).then(response => {
			expect(response.body.errors, 'errors of the mutation for Save to JCR').to.equal(undefined);
		});
		graphql(addAction, {parent: listPath, name: 'redirect', type: 'fmdb:redirectAction'}).then(response => {
			expect(response.body.errors, 'errors of the mutation for the redirect action').to.have.length.greaterThan(0);
		});
		primaryTypesUnder(listPath).should('deep.equal', ['fmdb:save2jcrAction']);
	});
});
