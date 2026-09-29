import {InputFileData, JahiaNode, NodeProperty} from './types';

export const INPUT_FILE_SIMPLE: InputFileData = {
	name: 'supportingDocument',
	title: 'Supporting document'
};

export const INPUT_FILE_MULTIPLE: InputFileData = {
	name: 'attachments',
	title: 'Attachments',
	accept: ['text/csv', 'application/pdf'],
	multiple: true,
	required: true
};

export function getInputFileNode(data: InputFileData = INPUT_FILE_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.helpText) properties.push({name: 'helpText', value: data.helpText, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.multiple !== undefined) properties.push({name: 'multiple', value: String(data.multiple), type: 'BOOLEAN'});
	if (data.accept && data.accept.length > 0) properties.push({name: 'accept', values: data.accept});

	return {
		name: data.name || 'fileInput',
		primaryNodeType: 'fmdb:inputFile',
		properties
	};
}

/** The documented default of the uploadAllowedTypes configuration. */
export const UPLOAD_ALLOWED_TYPES_DEFAULT = 'jpg,png,gif,webp,pdf,doc,docx,xls,xlsx,odt,ods,txt,csv,mp4,webm,ogv,mkv';

/**
 * Sets the file types every file field may accept. The configuration is
 * instance-global: specs that change it must restore UPLOAD_ALLOWED_TYPES_DEFAULT afterwards.
 */
export function setUploadAllowedTypes(types: string): Cypress.Chainable {
	return cy.runProvisioningScript({
		script: {
			fileContent: JSON.stringify([{
				editConfiguration: 'org.jahia.modules.formidable.uploads',
				properties: {uploadAllowedTypes: types}
			}]),
			type: 'application/json'
		}
	});
}
