import {getInputCheckboxNode} from './inputCheckbox';
import {InputCheckboxGroupData, JahiaNode, NodeProperty} from './types';

export const INPUT_CHECKBOX_GROUP_SIMPLE: InputCheckboxGroupData = {
	name: 'simpleCheckboxGroup',
	title: 'Select your interests',
	checkboxes: [
		{name: 'sports', title: 'Sports', value: 'sports', required: true},
		{name: 'music', title: 'Music', value: 'music'}
	]
};
export const INPUT_CHECKBOX_GROUP_COMPLETE: InputCheckboxGroupData = {
	name: 'completeCheckboxGroup',
	title: 'Required interests',
	required: true,
	checkboxes: [
		{name: 'reading', title: 'Reading', value: 'reading', required: true},
		{name: 'sports', title: 'Sports', value: 'sports', required: true, defaultChecked: true},
		{name: 'music', title: 'Music', value: 'music', required: true}
	]
};

export function getInputCheckboxGroupNode(data: InputCheckboxGroupData = INPUT_CHECKBOX_GROUP_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	const children = data.checkboxes?.map((checkbox) => getInputCheckboxNode(checkbox)) || [];
	return {
		name: data.name || 'checkboxGroup',
		primaryNodeType: 'fmdb:checkboxGroup',
		properties, ...(children.length > 0 && {children})
	};
}
