import {CONTENT_PATH, JCONTENT_SELECTORS, SITE_HOME_PATH} from "../constants";
import {addNode, publishAndWaitJobEnding} from "@jahia/cypress";
import {JContent} from '@jahia/jcontent-cypress/dist/page-object/jcontent';
import {FORMIDABLE_TEST_SITE} from "./site";
import {Form, FORM_HYDRATION_TIMEOUT_MS, isFormHydrated} from '../../page-object';
import {JahiaNode, NodeProperty} from './types';

interface CreateFormNodeOptions {
	actions?: JahiaNode[];
	mixins?: string[];
	properties?: NodeProperty[];
	pageProperties?: NodeProperty[];
	publishLanguages?: string[];
}

export interface LiveFormPageInfo {
	formId: string;
	formPath: string;
	pagePath: string;
	livePath: string;
	/** Path of the fmdb:formReference placing the form on the page — the node the Page Builder box belongs to. */
	referencePath: string;
}

export interface LiveFormSubmissionInfo {
	name: string;
	path: string;
}

interface LatestSubmissionQueryResponse {
	data?: {
		jcr?: {
			nodeByPath?: {
				descendants?: {
					nodes?: Array<{
						name: string;
						path: string;
						created: {value: string};
					}>;
				};
			};
		};
	};
	errors?: Array<{message?: string}>;
}

interface AddNodeResponse {
	data?: {
		jcr?: {
			addNode?: {
				uuid?: string;
			};
		};
	};
}

const buildFormChildren = (formElements: JahiaNode[], actions: JahiaNode[] = []): JahiaNode[] => ([
	{
		name: 'fields',
		primaryNodeType: 'fmdb:fieldList',
		properties: [],
		children: formElements
	},
	...(actions.length > 0
		? [{
			name: 'actions',
			primaryNodeType: 'fmdb:actionList',
			properties: [],
			children: actions
		}]
		: [])
]);

export const createFormNode = (
	formName: string,
	formTitle: string,
	formElements: JahiaNode[] = [],
	options: CreateFormNodeOptions = {}
) => {
	return addNode({
		parentPathOrId: CONTENT_PATH,
		name: formName,
		primaryNodeType: 'fmdb:form',
		mixins: options.mixins || [],
		properties: [
			{name: 'jcr:title', value: formTitle, language: 'en'},
			...(options.properties || [])
		],
		children: buildFormChildren(formElements, options.actions)
	});
};

export const createPublishedLiveFormPage = (
	formName: string,
	formTitle: string,
	formElements: JahiaNode[] = [],
	pageName: string = `${formName}-page`,
	pageTitle: string = formTitle,
	options: CreateFormNodeOptions = {}
): Cypress.Chainable<LiveFormPageInfo> => {
	const formPath = `${CONTENT_PATH}/${formName}`;
	const pagePath = `${SITE_HOME_PATH}/${pageName}`;
	const livePath = `home/${pageName}.html`;
	const referencePath = `${pagePath}/pagecontent/${formName}-reference`;
	let formId: string;

	return createFormNode(formName, formTitle, formElements, options)
		.then((response: AddNodeResponse) => {
			formId = response?.data?.jcr?.addNode?.uuid;
			if (!formId) {
				throw new Error(`Could not resolve UUID for form '${formName}': ${JSON.stringify(response)}`);
			}

			return addNode({
				parentPathOrId: SITE_HOME_PATH,
				name: pageName,
				primaryNodeType: 'jnt:page',
				properties: [
					{name: 'jcr:title', value: pageTitle, language: 'en'},
					{name: 'j:templateName', value: 'simple'},
					...(options.pageProperties || [])
				],
				children: [
					{
						name: 'pagecontent',
						primaryNodeType: 'jnt:contentList',
						properties: [],
						children: [
							{
								name: 'main-resource-display',
								primaryNodeType: 'jnt:mainResourceDisplay',
								properties: []
							},
							{
								name: `${formName}-reference`,
								primaryNodeType: 'fmdb:formReference',
								properties: [
									{name: 'j:node', value: formId, type: 'WEAKREFERENCE'}
								]
							}
						]
					}
				]
			});
		})
		.then(() => {
			publishAndWaitJobEnding(formPath, options.publishLanguages);
			publishAndWaitJobEnding(pagePath, options.publishLanguages);

			return cy.wrap<LiveFormPageInfo>({formId, formPath, pagePath, livePath, referencePath}, {log: false});
		});
};


/**
 * Opens a form in preview mode and returns the Form page object
 */
export function getFormPreview(formTitle: string): Form {
	const jcontent = JContent.visit(FORMIDABLE_TEST_SITE.key, 'en', 'content-folders/contents').switchToListMode();
	jcontent.getTable().getRowByLabel(formTitle).contextMenu().select('Preview');

	return new Form(
		cy.get(JCONTENT_SELECTORS.previewIframe)
			.its('0.contentDocument.body')
			.should('be.visible')
			.then(cy.wrap)
			.find('form')
	);
}

/**
 * @param query optional query string (without the leading "?"), for logic rules whose
 *              source is a URL parameter.
 */
/**
 * The first form of the page, once its React island has mounted. Every helper
 * that renders a form for interaction goes through here so no spec types, clicks
 * or attaches before hydration (the cause of most CI flakes).
 */
function getHydratedForm(): Form {
	return new Form(
		// The assertion below retries with the timeout of the command right before it,
		// so the budget has to sit on first(), not on get().
		cy.get('form.fmdb-form')
			.should('exist')
			.first({timeout: FORM_HYDRATION_TIMEOUT_MS})
			.should($form => {
				expect(isFormHydrated($form), 'form island hydrated').to.equal(true);
			})
	);
}

export function visitLiveForm(livePath: string, lang: string = 'en', query?: string): Form {
	const search = query ? `?${query}` : '';
	cy.visit(`/${lang}/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}${search}`);

	return getHydratedForm();
}

/**
 * Opens a form page in preview (default workspace, render servlet) and returns
 * the Form page object. Requires an authenticated session (cy.login()).
 */
export function visitPreviewForm(livePath: string, lang: string = 'en'): Form {
	cy.visit(`/cms/render/default/${lang}/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`);

	return getHydratedForm();
}

/**
 * Opens the page holding the form in edit mode and returns the Form page object.
 * `editframe` is the only URL that renders edit-mode markup directly: /cms/edit/...
 * redirects to the jContent SPA. Requires an authenticated session (cy.login()).
 *
 * @param pagePath absolute JCR path of the page, as returned by createPublishedLiveFormPage
 */
export function visitEditForm(pagePath: string, lang: string = 'en'): Form {
	cy.visit(`/cms/editframe/default/${lang}${pagePath}.html`);

	return new Form(
		cy.get('form.fmdb-form')
			.should('exist')
			.first()
	);
}

export const getLatestLiveFormSubmission = (formName: string): Cypress.Chainable<LiveFormSubmissionInfo> => {
	const submissionsRootPath = `/sites/${FORMIDABLE_TEST_SITE.key}/formidable-results/${formName}/submissions`;

	return cy.request<LatestSubmissionQueryResponse>({
		method: 'POST',
		url: '/modules/graphql',
		headers: {
			Origin: (Cypress.config('baseUrl') as string | null) ?? 'http://localhost:8080'
		},
		body: {
			query: `
				query LatestFormSubmission($submissionsRootPath: String!) {
					jcr(workspace: LIVE) {
						nodeByPath(path: $submissionsRootPath) {
							descendants(typesFilter: {types: ["fmdb:formSubmission"]}) {
								nodes {
									name
									path
									created: property(name: "jcr:created") {value}
								}
							}
						}
					}
				}
			`,
			variables: {submissionsRootPath}
		}
	}).then(response => {
		const graphQlError = response.body.errors?.[0]?.message;
		if (graphQlError) {
			throw new Error(`Could not query latest form submission: ${graphQlError}`);
		}

		const submissions = response.body.data?.jcr?.nodeByPath?.descendants?.nodes ?? [];
		const submission = submissions.sort((left, right) =>
			new Date(right.created.value).getTime() - new Date(left.created.value).getTime()
		)[0];
		if (!submission) {
			throw new Error(`No form submissions found under ${submissionsRootPath}`);
		}

		return cy.wrap({name: submission.name, path: submission.path}, {log: false});
	});
};
