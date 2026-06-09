import {CONTENT_PATH, JCONTENT_SELECTORS, SITE_HOME_PATH} from "../constants";
import {addNode, publishAndWaitJobEnding} from "@jahia/cypress";
import {JContent} from '@jahia/jcontent-cypress/dist/page-object/jcontent';
import {FORMIDABLE_TEST_SITE} from "./site";
import {Form} from '../../page-object';
import {JahiaNode, NodeProperty} from './types';

interface CreateFormNodeOptions {
	actions?: JahiaNode[];
	properties?: NodeProperty[];
}

export interface LiveFormPageInfo {
	formPath: string;
	pagePath: string;
	livePath: string;
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

	return createFormNode(formName, formTitle, formElements, options)
		.then((response: any) => {
			const formUuid = response?.data?.jcr?.addNode?.uuid;
			if (!formUuid) {
				throw new Error(`Could not resolve UUID for form '${formName}'`);
			}

			return addNode({
				parentPathOrId: SITE_HOME_PATH,
				name: pageName,
				primaryNodeType: 'jnt:page',
				properties: [
					{name: 'jcr:title', value: pageTitle, language: 'en'},
					{name: 'j:templateName', value: 'simple'}
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
									{name: 'j:node', value: formUuid, type: 'WEAKREFERENCE'}
								]
							}
						]
					}
				]
			});
		})
		.then(() => {
			publishAndWaitJobEnding(formPath);
			publishAndWaitJobEnding(pagePath);

			return cy.wrap<LiveFormPageInfo>({formPath, pagePath, livePath}, {log: false});
		});
};


/**
 * Opens a form in preview mode and returns the Form page object
 */
export function getFormPreview(formTitle: string): Form {
	const jcontent = JContent.visit(FORMIDABLE_TEST_SITE.key, 'en', 'content-folders/contents').switchToListMode();
	jcontent.getTable().getRowByLabel(formTitle).contextMenu().select('Preview');

	const formBody = cy.get(JCONTENT_SELECTORS.previewIframe)
		.its('0.contentDocument.body')
		.should('be.visible')
		.then(cy.wrap)
		.find('form');

	return new Form(formBody);
}

export function visitLiveForm(livePath: string): Form {
	cy.visit(`/en/sites/${FORMIDABLE_TEST_SITE.key}/${livePath}`);

	const formBody = cy.get('form.fmdb-form')
		.should('exist')
		.first();

	return new Form(formBody);
}
