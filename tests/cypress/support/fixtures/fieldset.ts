import {FieldsetData, JahiaNode, NodeProperty} from './types';

export const FIELDSET_PROFILE: FieldsetData = {
	name: 'profileDetails',
	title: 'Profile details'
};

export function getFieldsetNode(data: FieldsetData = FIELDSET_PROFILE): JahiaNode {
	const properties: NodeProperty[] = [];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	// j:view belongs to jmix:renderable, which the Content Editor adds with the property
	if (data.view) properties.push({name: 'j:view', value: data.view});

	return {
		name: data.name || 'fieldset',
		primaryNodeType: 'fmdb:fieldset',
		...(data.view ? {mixins: ['jmix:renderable']} : {}),
		properties,
		children: data.children || []
	};
}
