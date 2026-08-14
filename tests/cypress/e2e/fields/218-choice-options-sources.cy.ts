import gql from 'graphql-tag';
import {addNode, deleteNode, getNodeByPath, publishAndWaitJobEnding} from '@jahia/cypress';
import {
	createFormNode,
	createPublishedLiveFormPage,
	getCategoryChoiceFieldNode,
	getCategoryNode,
	getInputTextNode,
	getSourcedChoiceFieldNode,
	setOptionsSourcesConfig,
	visitLiveForm
} from '../../support/fixtures';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

const CATEGORY_ROOT = '/sites/systemsite/categories';
// Categories are global to the platform: unique per-run name + cleanup in both workspaces.
const SIZES_ROOT_NAME = `fmdb-choice-options-sizes-${Date.now()}`;
const SIZES_ROOT_PATH = `${CATEGORY_ROOT}/${SIZES_ROOT_NAME}`;

const GET_SOURCE_PREVIEW = gql`
	query getSourcePreview($fieldPath: String!, $parentPath: String!, $sourceKey: [String]!, $locale: String!) {
		forms {
			fieldConstraints(
				nodeUuidOrPath: $fieldPath,
				parentNodeUuidOrPath: $parentPath,
				primaryNodeType: "fmdb:select",
				fieldNodeType: "fmdbmix:sourcedOptions",
				fieldName: "fmdb:optionsSourceKey",
				context: [{key: "sourceKey", value: $sourceKey}],
				uiLocale: $locale,
				locale: $locale
			) {
				displayValue
				value {
					string
				}
			}
		}
	}
`;

type FieldConstraintsResponse = {
	errors?: Array<{message: string}>;
	data?: {
		forms?: {
			fieldConstraints?: Array<{
				displayValue?: string;
				value?: {string?: string};
			}>;
		};
	};
};

describe('Form fields - 218 Choice options sources', () => {
	useFormidableSite();

	before(() => {
		cy.login();

		// 'country' is a platform choicelist initializer: no extra module needed.
		setOptionsSourcesConfig(['countries|Countries|country']);

		addNode({parentPathOrId: CATEGORY_ROOT, ...getCategoryNode(SIZES_ROOT_NAME, 'Sizes', 'Tailles')});
		addNode({parentPathOrId: SIZES_ROOT_PATH, ...getCategoryNode('small', 'Small', 'Petit')});
		addNode({parentPathOrId: SIZES_ROOT_PATH, ...getCategoryNode('medium', 'Medium', 'Moyen')});
		addNode({parentPathOrId: SIZES_ROOT_PATH, ...getCategoryNode('large', 'Large', 'Grand')});
		publishAndWaitJobEnding(SIZES_ROOT_PATH, ['en', 'fr']);

		cy.logout();
	});

	after(() => {
		cy.login();
		deleteNode(SIZES_ROOT_PATH, 'LIVE');
		deleteNode(SIZES_ROOT_PATH);
		cy.logout();
	});

	it('renders a sourced select with the options the source resolves to, in the page language', () => {
		createPublishedLiveFormPage(
			'sourced-select-form',
			'Sourced Select Form',
			[
				getSourcedChoiceFieldNode({
					primaryNodeType: 'fmdb:select',
					name: 'country',
					title: 'Country',
					sourceKey: 'countries'
				})
			],
			undefined,
			undefined,
			{
				properties: [{name: 'jcr:title', value: 'Formulaire pays', language: 'fr'}],
				pageProperties: [{name: 'jcr:title', value: 'Formulaire pays', language: 'fr'}],
				publishLanguages: ['en', 'fr']
			}
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);
			const select = form.getSelectInput('country');

			select.shouldBeVisible().shouldHaveOption('France');
			select.get().find('option').should('have.length.greaterThan', 100);
			// Option values are what the source resolves, not display labels.
			select.get().find('option:contains("France")').should('have.attr', 'value', 'FR');

			// The same source resolves localized labels in the French page.
			const formFr = visitLiveForm(livePath, 'fr');
			formFr.getSelectInput('country').shouldBeVisible().shouldHaveOption('Allemagne');
		});
	});

	it('renders a category radio group from the published children of the picked category', () => {
		getNodeByPath(SIZES_ROOT_PATH).then(response => {
			const rootUuid: string = response.data.jcr.nodeByPath.uuid;

			createPublishedLiveFormPage(
				'category-radio-form',
				'Category Radio Form',
				[
					getCategoryChoiceFieldNode({
						primaryNodeType: 'fmdb:radio',
						name: 'shirtSize',
						title: 'Shirt size',
						rootCategoryUuid: rootUuid
					})
				],
				undefined,
				undefined,
				{
					properties: [{name: 'jcr:title', value: 'Formulaire tailles', language: 'fr'}],
					pageProperties: [{name: 'jcr:title', value: 'Formulaire tailles', language: 'fr'}],
					publishLanguages: ['en', 'fr']
				}
			).then(({livePath}) => {
				const form = visitLiveForm(livePath);
				const group = form.getRadioGroup('shirtSize');

				group.get().find('input[type="radio"]').should('have.length', 3);
				// Values are the category names, labels their localized titles.
				group.getRadio('Small').shouldHaveValue('small');
				group.getRadio('Medium').shouldHaveValue('medium');
				group.getRadio('Large').shouldHaveValue('large');

				const formFr = visitLiveForm(livePath, 'fr');
				formFr.getRadioGroup('shirtSize').getRadio('Petit').shouldHaveValue('small');
			});
		});
	});

	it('blocks submission when the source of a required field cannot deliver', () => {
		createPublishedLiveFormPage(
			'source-down-required-form',
			'Source Down Required Form',
			[
				getSourcedChoiceFieldNode({
					primaryNodeType: 'fmdb:select',
					name: 'brokenRequired',
					title: 'Broken required',
					sourceKey: 'ghost',
					required: true
				})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('.fmdb-options-source-error')
				.should('be.visible')
				.should('have.attr', 'data-fmdb-source-error', 'blocking');
			form.getSubmitButton().get().should('be.disabled');
		});
	});

	it('keeps the form usable when the source of an optional field cannot deliver', () => {
		createPublishedLiveFormPage(
			'source-down-optional-form',
			'Source Down Optional Form',
			[
				getSourcedChoiceFieldNode({
					primaryNodeType: 'fmdb:select',
					name: 'brokenOptional',
					title: 'Broken optional',
					sourceKey: 'ghost'
				}),
				getInputTextNode({name: 'fullName', title: 'Full name'})
			]
		).then(({livePath}) => {
			const form = visitLiveForm(livePath);

			form.get().find('.fmdb-options-source-error')
				.should('be.visible')
				.should('have.attr', 'data-fmdb-source-error', 'optional');
			form.getSubmitButton().get().should('not.be.disabled');

			form.getTextInput('fullName').type('Alice');
			form.submit();
			form.getSuccessMessage().should('be.visible');
		});
	});

	it('exposes the resolved options to the editor source preview', () => {
		createFormNode(
			'source-preview-form',
			'Source Preview Form',
			[
				getSourcedChoiceFieldNode({
					primaryNodeType: 'fmdb:select',
					name: 'previewCountry',
					title: 'Preview country',
					sourceKey: 'countries'
				})
			]
		).then(() => {
			const fieldPath = `${CONTENT_PATH}/source-preview-form/fields/previewCountry`;
			const parentPath = `${CONTENT_PATH}/source-preview-form/fields`;

			cy.apollo({
				query: GET_SOURCE_PREVIEW,
				variables: {fieldPath, parentPath, sourceKey: ['countries'], locale: 'fr'}
			}).then((response: FieldConstraintsResponse) => {
				expect(response.errors, 'GraphQL errors for the countries preview').to.be.undefined;

				const constraints = response.data?.forms?.fieldConstraints ?? [];
				expect(constraints.length).to.be.greaterThan(100);

				const germany = constraints.find(constraint => constraint.value?.string === 'DE');
				expect(germany?.displayValue, 'localized label of the DE constraint').to.eq('Allemagne');
			});

			// An unknown key resolves to a GraphQL error, surfaced to the editor as
			// 'preview unavailable'. cy.apollo strips the errors array, so the visible
			// contract is the null constraints payload.
			cy.apollo({
				query: GET_SOURCE_PREVIEW,
				variables: {fieldPath, parentPath, sourceKey: ['ghost'], locale: 'en'},
				errorPolicy: 'all'
			}).then((response: FieldConstraintsResponse) => {
				expect(response.data?.forms?.fieldConstraints ?? null, 'constraints for an unknown source key').to.be.null;
			});
		});
	});
});
