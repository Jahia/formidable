import {addNode, deleteNode, getNodeByPath, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	createPublishedLiveFormPage,
	getCategoryChoiceFieldNode,
	getCategoryNode,
	getContentChoiceFieldNode,
	getSourcedChoiceFieldNode,
	getTitledTextNode,
	setOptionsSourcesConfig
} from '../../support/fixtures';
import {CONTENT_PATH} from '../../support/constants';
import {
	expectErrorResponse,
	expectSuccessResponse,
	postDirectMultipartSubmission,
	useFormidableSite,
	withSameOriginHeaders
} from './support';

const CATEGORY_ROOT = '/sites/systemsite/categories';
// Categories are global to the platform: unique per-run name + cleanup in both workspaces.
const SCREENS_ROOT_NAME = `fmdb-sourced-tampering-screens-${Date.now()}`;
const SCREENS_ROOT_PATH = `${CATEGORY_ROOT}/${SCREENS_ROOT_NAME}`;

const SOURCES_CONFIG = ['countries|Countries|country'];

const AGENCIES_ROOT_PATH = `${CONTENT_PATH}/tampering-agencies`;

/**
 * Waits until a direct submission of the given fields returns the expected
 * status. editConfiguration reaches the options sources asynchronously (the
 * ConfigAdmin update is dispatched on its own event thread), so the first
 * expectation after a configuration toggle polls instead of asserting the
 * very first response.
 */
function waitForSubmissionStatus(
		formId: string,
		fields: Record<string, string>,
		status: number,
		errorMsg: string
): Cypress.Chainable {
	return cy.waitUntil(
		() => postDirectMultipartSubmission({formId, fields, headers: withSameOriginHeaders()})
			.then(response => response.status === status),
		{timeout: 15000, interval: 500, errorMsg}
	);
}

describe('Security - 46 sourced options tampering', () => {
	useFormidableSite();

	let optionalFormId: string;
	let requiredFormId: string;

	before(() => {
		cy.login();

		setOptionsSourcesConfig(SOURCES_CONFIG);

		addNode({parentPathOrId: CATEGORY_ROOT, ...getCategoryNode(SCREENS_ROOT_NAME, 'Screens', 'Écrans')});
		addNode({parentPathOrId: SCREENS_ROOT_PATH, ...getCategoryNode('oled', 'OLED', 'OLED')});
		addNode({parentPathOrId: SCREENS_ROOT_PATH, ...getCategoryNode('led', 'LED', 'LED')});
		publishAndWaitJobEnding(SCREENS_ROOT_PATH, ['en', 'fr']);

		// Content-mode targets. The edit-only draft is created later, once the
		// forms are published: publishing a form publishes its referenced options
		// root with its subtree, so an earlier draft would be published along.
		addNode({parentPathOrId: CONTENT_PATH, name: 'tampering-agencies', primaryNodeType: 'jnt:contentFolder', properties: []});
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('paris', 'Paris agency', 'Agence de Paris')});
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('lyon', 'Lyon agency', 'Agence de Lyon')});
		publishAndWaitJobEnding(AGENCIES_ROOT_PATH, ['en', 'fr']);

		getNodeByPath(SCREENS_ROOT_PATH).then(response => {
			const rootUuid: string = response.data.jcr.nodeByPath.uuid;

			getNodeByPath(AGENCIES_ROOT_PATH).then(agenciesResponse => {
				const agenciesRootUuid: string = agenciesResponse.data.jcr.nodeByPath.uuid;

				createPublishedLiveFormPage(
					'sourced-tampering-form',
					'Sourced tampering form',
					[
						getSourcedChoiceFieldNode({
							primaryNodeType: 'fmdb:select',
							name: 'country',
							title: 'Country',
							sourceKey: 'countries'
						}),
						getCategoryChoiceFieldNode({
							primaryNodeType: 'fmdb:radio',
							name: 'screenType',
							title: 'Screen type',
							rootCategoryUuid: rootUuid
						}),
						getContentChoiceFieldNode({
							primaryNodeType: 'fmdb:select',
							name: 'agency',
							title: 'Agency',
							rootNodeUuid: agenciesRootUuid,
							nodeType: 'jnt:text'
						})
					]
				).then(({formId}) => {
					optionalFormId = formId;
				});
			});

			createPublishedLiveFormPage(
				'sourced-required-form',
				'Sourced required form',
				[
					getSourcedChoiceFieldNode({
						primaryNodeType: 'fmdb:select',
						name: 'requiredCountry',
						title: 'Required country',
						sourceKey: 'countries',
						required: true
					})
				]
			).then(({formId}) => {
				requiredFormId = formId;

				// Every form is published by now: this draft stays in the edit
				// workspace, invisible to the live submit-time resolution.
				addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('draft', 'Draft agency', 'Agence brouillon')});
			});
		});

		cy.logout();
	});

	after(() => {
		cy.login();
		// The options sources configuration is instance-global: leave it as declared.
		setOptionsSourcesConfig(SOURCES_CONFIG);
		deleteNode(SCREENS_ROOT_PATH, 'LIVE');
		deleteNode(SCREENS_ROOT_PATH);
		cy.logout();
	});

	it('accepts values that the sources resolve', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'oled', agency: 'paris'},
			headers: withSameOriginHeaders()
		}).then(expectSuccessResponse);
	});

	it('rejects a forged value on a sourced field', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'NOT_A_COUNTRY', screenType: 'oled'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});

	it('rejects a forged value on a category field', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'crt'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});

	it('rejects a forged value on a content field', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'oled', agency: 'nowhere'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});

	it('rejects the value of an unpublished content on a content field', () => {
		cy.logout();

		// 'draft' exists under the picked root but only in the edit workspace:
		// the submit-time resolution runs in live and must not see it.
		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'oled', agency: 'draft'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));
	});

	it('rejects the value of a published content hidden from the visitor by ACL', () => {
		cy.login();
		// 'hidden' is published under the picked root but unreadable by guest:
		// absent from the rendered options, it must stay rejected at submit time
		// too — the re-resolution runs with the requester's session, and only a
		// system-session resolution would let this value through.
		addNode({parentPathOrId: AGENCIES_ROOT_PATH, ...getTitledTextNode('hidden', 'Hidden agency', 'Agence cachée')});
		publishAndWaitJobEnding(`${AGENCIES_ROOT_PATH}/hidden`, ['en', 'fr']);
		cy.executeGroovy('groovy/breakAclInheritanceInDefaultAndLive.groovy', {
			__PATH__: `${AGENCIES_ROOT_PATH}/hidden`
		}).then(result => cy.log(String(result)));
		cy.logout();

		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'oled', agency: 'hidden'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));

		// The visible siblings keep flowing: the ACL trim is per-node, not a
		// side effect breaking the whole field.
		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'oled', agency: 'paris'},
			headers: withSameOriginHeaders()
		}).then(expectSuccessResponse);
	});

	it('rejects any submitted value when the source is gone, but still accepts omitting the optional field', () => {
		cy.login();
		setOptionsSourcesConfig([]);
		cy.logout();

		// A value that used to be valid can no longer be verified: rejected.
		waitForSubmissionStatus(optionalFormId, {country: 'FR', screenType: 'oled'}, 400,
			'the source removal never reached the submission validation');
		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'oled'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));

		// Leaving the optional unresolvable field empty keeps the form usable.
		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {screenType: 'oled'},
			headers: withSameOriginHeaders()
		}).then(expectSuccessResponse);

		cy.login();
		setOptionsSourcesConfig(SOURCES_CONFIG);
		cy.logout();

		// Restoring the source restores submissions.
		waitForSubmissionStatus(optionalFormId, {country: 'FR', screenType: 'oled'}, 200,
			'the restored source never reached the submission validation');
		postDirectMultipartSubmission({
			formId: optionalFormId,
			fields: {country: 'FR', screenType: 'oled'},
			headers: withSameOriginHeaders()
		}).then(expectSuccessResponse);
	});

	it('rejects an empty required field whose source is gone', () => {
		cy.logout();

		postDirectMultipartSubmission({
			formId: requiredFormId,
			fields: {requiredCountry: 'FR'},
			headers: withSameOriginHeaders()
		}).then(expectSuccessResponse);

		cy.login();
		setOptionsSourcesConfig([]);
		cy.logout();

		// The undeclared field keeps the multipart body well-formed and is ignored
		// by the collector, so the submission is an empty one for the form fields.
		waitForSubmissionStatus(requiredFormId, {undeclared: 'x'}, 400,
			'the source removal never reached the submission validation');
		postDirectMultipartSubmission({
			formId: requiredFormId,
			fields: {undeclared: 'x'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));

		cy.login();
		setOptionsSourcesConfig(SOURCES_CONFIG);
		cy.logout();

		// The configuration is instance-global: leave the spec only once the
		// restored source is effective again.
		waitForSubmissionStatus(requiredFormId, {requiredCountry: 'FR'}, 200,
			'the restored source never reached the submission validation');
	});
});
