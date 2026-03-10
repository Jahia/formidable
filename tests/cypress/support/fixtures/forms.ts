import {CONTENT_PATH, JCONTENT_SELECTORS} from "../constants";
import {addNode} from "@jahia/cypress";
import {JContent} from '@jahia/jcontent-cypress/dist/page-object/jcontent';
import {FORMIDABLE_TEST_SITE} from "./site";
import {Form} from '../../page-object';
import {JahiaNode} from './types';

export const createFormNode = (formName: string, formTitle: string, children: JahiaNode[] = []) => {
	return addNode({
		parentPathOrId: CONTENT_PATH,
		name: formName,
		primaryNodeType: 'fmdb:form',
		properties: [{name: 'jcr:title', value: formTitle}],
		children
	});
};


/**
 * Opens a form in preview mode and returns the Form page object
 */
export function getFormPreview(formTitle: string): Form {
	const jcontent = JContent.visit(FORMIDABLE_TEST_SITE.key, 'en', 'content-folders/contents').switchToStructuredView();
	jcontent.getTable().getRowByLabel(formTitle).contextMenu().select('Preview');

	const formBody = cy.get(JCONTENT_SELECTORS.previewIframe)
		.its('0.contentDocument.body')
		.should('be.visible')
		.then(cy.wrap)
		.find('form');

	return new Form(formBody);
}
