import {InputColorData, JahiaNode, NodeProperty} from './types';

export const INPUT_COLOR_SIMPLE: InputColorData = {name: 'simpleColor', title: 'Choose Color'};
export const INPUT_COLOR_COMPLETE: InputColorData = {
	name: 'completeColor',
	title: 'Pick your favorite color',
	required: true,
	defaultValue: '#ff5733'
};

export function getInputColorNode(data: InputColorData = INPUT_COLOR_SIMPLE): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.required !== undefined) properties.push({name: 'required', value: String(data.required), type: 'BOOLEAN'});
	if (data.defaultValue !== undefined) properties.push({name: 'defaultValue', value: data.defaultValue});
	return {name: data.name || 'colorInput', primaryNodeType: 'fmdb:inputColor', properties};
}
