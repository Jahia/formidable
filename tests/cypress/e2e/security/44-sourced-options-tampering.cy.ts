import {addNode, deleteNode, getNodeByPath, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	createPublishedLiveFormPage,
	getCategoryChoiceFieldNode,
	getCategoryNode,
	getSourcedChoiceFieldNode,
	setOptionsSourcesConfig
} from '../../support/fixtures';
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

describe('Security - sourced options tampering', () => {
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

		getNodeByPath(SCREENS_ROOT_PATH).then(response => {
			const rootUuid: string = response.data.jcr.nodeByPath.uuid;

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
					})
				]
			).then(({formId}) => {
				optionalFormId = formId;
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
			fields: {country: 'FR', screenType: 'oled'},
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

	it('rejects any submitted value when the source is gone, but still accepts omitting the optional field', () => {
		cy.login();
		setOptionsSourcesConfig([]);
		cy.logout();

		// A value that used to be valid can no longer be verified: rejected.
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
		postDirectMultipartSubmission({
			formId: requiredFormId,
			fields: {undeclared: 'x'},
			headers: withSameOriginHeaders()
		}).then(response => expectErrorResponse(response, 400, 'FMDB-010'));

		cy.login();
		setOptionsSourcesConfig(SOURCES_CONFIG);
		cy.logout();
	});
});
