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
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.multiple !== undefined) properties.push({name: 'multiple', value: String(data.multiple), type: 'BOOLEAN'});
	if (data.accept && data.accept.length > 0) properties.push({name: 'accept', values: data.accept});

	return {
		name: data.name || 'fileInput',
		primaryNodeType: 'fmdb:inputFile',
		properties
	};
}
