import {FORWARD_SINK_PATH} from '../../support/constants';
import {
	FORWARD_SINK_TARGET_ID,
	UPLOAD_SPLIT_HEADERS,
	createPublishedLiveFormPage,
	getForwardActionNode,
	getInputFileNode,
	getInputTextNode,
	getLatestLiveFormSubmission,
	getLatestLiveSubmissionValues,
	getSaveToJcrActionNode,
	getUploadObserverActionNode,
	getUploadQuarantiningActionNode
} from '../../support/fixtures';
import {SAME_ORIGIN, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';

const BOUNDARY = '----FormidableCypressBoundary';
const FILE_FIELD = 'supportingDocument';
const FILE_NAME = 'claim-note.txt';

/** A submission of the text field and one small text file, as a browser posts it. */
const submissionWithAFile = (fullName: string): string =>
	`--${BOUNDARY}\r\n`
	+ 'Content-Disposition: form-data; name="fullName"\r\n\r\n'
	+ `${fullName}\r\n`
	+ `--${BOUNDARY}\r\n`
	+ `Content-Disposition: form-data; name="${FILE_FIELD}"; filename="${FILE_NAME}"\r\n`
	+ 'Content-Type: text/plain\r\n\r\n'
	+ 'A note attached to the claim.\r\n'
	+ `--${BOUNDARY}--\r\n`;

const editConfiguration = (pid: string, properties: Record<string, string>): Cypress.Chainable => cy.runProvisioningScript({
	script: {
		fileContent: JSON.stringify([{editConfiguration: pid, properties}]),
		type: 'application/json'
	}
});

/** The administrator's gate on development forward targets: the sink is plain HTTP on this instance. */
const setDevelopmentForwardTargets = (enabled: boolean): Cypress.Chainable =>
	editConfiguration('org.jahia.modules.formidable.formActions', {enableDevForwardTargets: String(enabled)});

/**
 * Declares the forward targets of the instance, the sink alone or none. The targets are instance-global and another
 * spec replaces them all, so this spec declares the one it needs and leaves none behind.
 */
const setForwardTargets = (targets: Array<Record<string, string>>): Cypress.Chainable =>
	cy.executeGroovy('groovy/replaceFactoryConfigurations.groovy', {
		__FACTORY_PID__: 'org.jahia.modules.formidable.formActions.target',
		__ENTRIES__: JSON.stringify(targets)
	});

/** The sink as a forward target: plain HTTP on the instance itself, hence a development target. */
const FORWARD_SINK_TARGET = {
	id: FORWARD_SINK_TARGET_ID,
	label: 'Samples forward sink',
	url: `http://localhost:8080${FORWARD_SINK_PATH}`,
	development: 'true'
};

/** The operator setting that makes the quarantine mandatory, for every form of the instance. */
const setUploadsRequireQuarantine = (required: boolean): Cypress.Chainable =>
	editConfiguration('org.jahia.modules.formidable.uploads', {uploadsRequireQuarantine: String(required)});

interface ForwardSinkState {
	count: number;
	fields: string[];
	files: string[];
}

const resetForwardSink = (): Cypress.Chainable =>
	cy.request({method: 'POST', url: `${FORWARD_SINK_PATH}/reset`, headers: {Origin: SAME_ORIGIN}});

/** What the last forward carried: the number of forwards since the reset, and the names of its value and file parts. */
const readForwardSink = (): Cypress.Chainable<ForwardSinkState> =>
	cy.request<ForwardSinkState>(FORWARD_SINK_PATH).its('body');

/** The names of the files Save to JCR stored under the latest live submission of the form. */
const storedFileNames = (formName: string): Cypress.Chainable<string[]> =>
	getLatestLiveFormSubmission(formName).then(({path}) => cy.request({
		method: 'POST',
		url: '/modules/graphql',
		headers: {Origin: SAME_ORIGIN},
		body: {
			query: 'query($path: String!) { jcr(workspace: LIVE) { nodeByPath(path: $path) { descendants(typesFilter: {types: ["jnt:file"]}) { nodes { name } } } } }',
			variables: {path}
		}
	})).then(response => {
		const nodes = response.body.data.jcr.nodeByPath.descendants.nodes as Array<{name: string}>;
		return nodes.map(node => node.name);
	});

const fields = () => [
	getInputTextNode({name: 'fullName', title: 'Full name'}),
	getInputFileNode({name: FILE_FIELD, title: 'Supporting document'})
];

/**
 * The split of the uploads (docs/adr/0002, tests/scenarios/quarantine.md): a form whose action list holds a
 * quarantining action hands its uploads to that action alone. The samples module ships the three observables: a
 * quarantining action and an observer, which each report the number of files they received in a response header, and
 * the receiver of a forward target, which answers the names of the parts the last forward carried.
 */
describe('Quarantine - 95 The uploads of a form reach its quarantining action alone', () => {
	useFormidableSite();

	before(() => {
		setForwardTargets([FORWARD_SINK_TARGET]);
		setDevelopmentForwardTargets(true);
		setUploadsRequireQuarantine(false);
	});

	beforeEach(() => {
		resetForwardSink();
	});

	after(() => {
		setUploadsRequireQuarantine(false);
		setDevelopmentForwardTargets(false);
		setForwardTargets([]);
	});

	it('Q07, Q08: on a quarantined form, the forward and the action that did not opt in receive no file', () => {
		// The quarantining action is the last of the list: the three actions before it run first, and still get no file.
		const formName = 'quarantined-form';
		createPublishedLiveFormPage(formName, 'Quarantined form', fields(), undefined, undefined, {
			actions: [
				getSaveToJcrActionNode(),
				getForwardActionNode({targetId: FORWARD_SINK_TARGET_ID}),
				getUploadObserverActionNode(),
				getUploadQuarantiningActionNode()
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Ada'), headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.headers[UPLOAD_SPLIT_HEADERS.quarantining], 'files of the quarantining action').to.eq('1');
				expect(response.headers[UPLOAD_SPLIT_HEADERS.observer], 'files of the action that did not opt in').to.eq('0');
			});
		});

		readForwardSink().then(forwarded => {
			expect(forwarded.count, 'forwards received').to.eq(1);
			expect(forwarded.fields, 'value parts forwarded').to.include('fullName');
			expect(forwarded.files, 'file parts forwarded').to.deep.equal([]);
		});

		// Save to JCR did not opt in either: it stored the values, and no file.
		cy.login();
		getLatestLiveSubmissionValues(formName).its('fullName').should('eq', 'Ada');
		storedFileNames(formName).should('deep.equal', []);
	});

	it('Q14: a form with no quarantining action hands its file to every action, as before', () => {
		const formName = 'plain-upload-form';
		createPublishedLiveFormPage(formName, 'Plain upload form', fields(), undefined, undefined, {
			actions: [
				getSaveToJcrActionNode(),
				getForwardActionNode({targetId: FORWARD_SINK_TARGET_ID}),
				getUploadObserverActionNode()
			]
		}).then(({formId}) => {
			cy.logout();
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Grace'), headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.headers[UPLOAD_SPLIT_HEADERS.observer], 'files of the observer').to.eq('1');
			});
		});

		readForwardSink().then(forwarded => {
			expect(forwarded.count, 'forwards received').to.eq(1);
			expect(forwarded.files, 'file parts forwarded').to.deep.equal([FILE_FIELD]);
		});

		cy.login();
		getLatestLiveSubmissionValues(formName).its('fullName').should('eq', 'Grace');
		storedFileNames(formName).should('deep.equal', [FILE_NAME]);
	});

	it('Q15: with a mandatory quarantine, a form with no quarantining action keeps its values and hands its file to no action', () => {
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
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Hedy'), headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.headers[UPLOAD_SPLIT_HEADERS.observer], 'files of the observer').to.eq('0');
			});
		});

		readForwardSink().then(forwarded => {
			expect(forwarded.count, 'forwards received').to.eq(1);
			expect(forwarded.fields, 'value parts forwarded').to.include('fullName');
			expect(forwarded.files, 'file parts forwarded').to.deep.equal([]);
		});

		cy.login();
		getLatestLiveSubmissionValues(formName).its('fullName').should('eq', 'Hedy');
		storedFileNames(formName).should('deep.equal', []);
	});
});
