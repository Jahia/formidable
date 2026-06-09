import {JahiaNode, NodeProperty, RichTextData} from './types';

export const RICH_TEXT_CONTENT: RichTextData = {
	name: 'introRichText',
	text: '<p><strong>Welcome</strong> to the form.</p><p><a href=\"https://example.com/docs\">Read the documentation</a></p><p><img src=\"https://via.placeholder.com/120x80\" alt=\"Placeholder illustration\" /></p>'
};

export function getRichTextNode(data: RichTextData = RICH_TEXT_CONTENT): JahiaNode {
	const properties: NodeProperty[] = [
		{name: 'text', value: data.text, language: 'en'}
	];

	return {
		name: data.name || 'richText',
		primaryNodeType: 'fmdb:richText',
		properties
	};
}
