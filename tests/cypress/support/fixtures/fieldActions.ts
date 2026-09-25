import {JahiaNode, NodeProperty} from './types';

/**
 * Builders for the field actions of a field (children of its 'actions' list, under the
 * fmdbmix:fieldActions switch). The feedback settings — when the check runs, what a refusal
 * does, what an unanswered check means, the visitor's message — reach every field action through
 * the engine's fmdbmix:fieldActionFeedback mixin; a setting left out keeps its CND default
 * (blur, block, accept, the bundle's message).
 */

export interface BlockedWordsFieldActionData {
	name?: string;
	title?: string;
	/** A value containing one of these is refused. */
	words: string[];
	trigger?: 'blur' | 'submit';
	severity?: 'block' | 'warn';
	whenUnavailable?: 'accept' | 'reject';
	/** Rich text, `${value}` interpolated by the engine (English). */
	rejectionMessage?: string;
}

/**
 * The samples module's field action (fmdbsample:blockedWordsAction, formidable-test-module-samples-java):
 * a third-party type, declared with its label, tooltip and icon outside formidable-engine, refusing a
 * value that contains one of its words.
 */
export function getBlockedWordsFieldActionNode(data: BlockedWordsFieldActionData): JahiaNode {
	const properties: NodeProperty[] = [{name: 'words', values: data.words}];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.trigger) properties.push({name: 'trigger', value: data.trigger});
	if (data.severity) properties.push({name: 'severity', value: data.severity});
	if (data.whenUnavailable) properties.push({name: 'whenUnavailable', value: data.whenUnavailable});
	if (data.rejectionMessage) properties.push({name: 'rejectionMessage', value: data.rejectionMessage, language: 'en'});

	return {
		name: data.name || 'blockedWords',
		primaryNodeType: 'fmdbsample:blockedWordsAction',
		mixins: ['fmdbmix:fieldActionFeedback'],
		properties
	};
}

/**
 * Switches the field's actions on, as the editor's Enable field actions switch does — the mixin and
 * its 'actions' list — with the given actions in execution order. One addNode mutation creates it
 * all: the mixin's autocreated list and the declared child of the same name meet in one node.
 */
export function withFieldActions(field: JahiaNode, actions: JahiaNode[] = []): JahiaNode {
	return {
		...field,
		mixins: [...(field.mixins ?? []), 'fmdbmix:fieldActions'],
		children: [
			...(field.children ?? []),
			{name: 'actions', primaryNodeType: 'fmdb:fieldActionList', properties: [], children: actions}
		]
	};
}
