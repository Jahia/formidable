import {FORWARD_SINK_PATH} from '../../support/constants';
import {
	FORWARD_SINK_TARGET_ID,
	UPLOAD_OBSERVER_HEADER,
	createPublishedLiveFormPage,
	getForwardActionNode,
	getLatestLiveFormSubmission,
	getLatestLiveSubmissionValues,
	getSaveToJcrActionNode,
	getUploadObserverActionNode
} from '../../support/fixtures';
import {SAME_ORIGIN, postDirectMultipartSubmission, withSameOriginHeaders} from '../security/support';
import {useFormidableSite} from '../support/useFormidableSite';
import {
	FILE_FIELD,
	FILE_NAME,
	fields,
	setDevelopmentForwardTargets,
	setUploadsRequireQuarantine,
	submissionWithAFile
} from './support';

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

/**
 * The uploads of a form with the quarantine switch off (docs/adr/0002, tests/scenarios/quarantine.md). The samples
 * module ships the two observables: an observer, which reports the number of files it received in a response header,
 * and the receiver of a forward target, which answers the names of the parts the last forward carried.
 */
describe('Quarantine - 95 The uploads of a form with the switch off', () => {
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
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Grace'), headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.headers[UPLOAD_OBSERVER_HEADER], 'files of the observer').to.eq('1');
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
			postDirectMultipartSubmission({formId, body: submissionWithAFile('Hedy'), headers: withSameOriginHeaders()}).then(response => {
				expect(response.status).to.eq(200);
				expect(response.headers[UPLOAD_OBSERVER_HEADER], 'files of the observer').to.eq('0');
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
