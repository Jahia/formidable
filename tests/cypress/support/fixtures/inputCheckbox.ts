import {InputCheckboxData, JahiaNode, NodeProperty} from './types';

export const INPUT_CHECKBOX_SIMPLE: InputCheckboxData = {
	name: 'simpleCheckbox',
	title: 'Accept Terms',
	value: 'accepted'
};
export const INPUT_CHECKBOX_COMPLETE: InputCheckboxData = {
	name: 'completeCheckbox',
	title: 'I agree to terms',
	value: 'agreed',
	required: true,
	defaultChecked: true
};

export function getInputCheckboxNode(data: InputCheckboxData = INPUT_CHECKBOX_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.value) properties.push({name: 'value', value: data.value});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.defaultChecked !== undefined) properties.push({
		name: 'defaultChecked',
		value: String(data.defaultChecked),
		type: 'BOOLEAN'
	});
	return {name: data.name || 'checkboxInput', primaryNodeType: 'fmdb:inputCheckbox', properties};
}
