import {FieldsetData, JahiaNode, NodeProperty} from './types';

export const FIELDSET_PROFILE: FieldsetData = {
	name: 'profileDetails',
	title: 'Profile details'
};

export function getFieldsetNode(data: FieldsetData = FIELDSET_PROFILE): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});

	return {
		name: data.name || 'fieldset',
		primaryNodeType: 'fmdb:fieldset',
		properties,
		children: data.children || []
	};
}
