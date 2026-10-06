import {InputHiddenData, JahiaNode, NodeProperty} from './types';

export function getInputHiddenNode(data: InputHiddenData): JahiaNode {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.value !== undefined) properties.push({name: 'value', value: data.value});
	return {name: data.name || 'hiddenInput', primaryNodeType: 'fmdb:inputHidden', properties};
}
