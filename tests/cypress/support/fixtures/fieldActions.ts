import {JahiaNode, NodeProperty} from './types';

/**
 * Builders for the field actions of a field (children of its 'actions' list, under the
 * fmdbmix:fieldActions switch). The feedback settings — when the check runs, what a refusal
 * does, what an unanswered check means, the visitor's message — reach every field action through
 * the engine's fmdbmix:fieldActionFeedback mixin; a setting left out keeps its CND default
 * (blur, block, accept, the bundle's message).
 */

export interface FieldActionFeedbackData {
	name?: string;
	title?: string;
	trigger?: 'blur' | 'submit';
	severity?: 'block' | 'warn';
	whenUnavailable?: 'accept' | 'reject';
	/** Rich text, `${value}` interpolated by the engine (English). */
	rejectionMessage?: string;
}

export interface BlockedWordsFieldActionData extends FieldActionFeedbackData {
	/** A value containing one of these is refused. */
	words: string[];
}

export interface MinimumWordsFieldActionData extends FieldActionFeedbackData {
	/** A value with fewer words is refused; the CND default is 3. */
	minimumWords?: number;
}

/** The properties of fmdbmix:fieldActionFeedback the data sets; the rest keeps the CND defaults. */
const feedbackProperties = (data: FieldActionFeedbackData): NodeProperty[] => {
	const properties: NodeProperty[] = [];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.trigger) properties.push({name: 'trigger', value: data.trigger});
	if (data.severity) properties.push({name: 'severity', value: data.severity});
	if (data.whenUnavailable) properties.push({name: 'whenUnavailable', value: data.whenUnavailable});
	if (data.rejectionMessage) properties.push({name: 'rejectionMessage', value: data.rejectionMessage, language: 'en'});
	return properties;
};

/**
 * The samples module's field action written in Java (fmdbsample:blockedWordsAction,
 * formidable-test-module-samples-java): a third-party type, declared with its label, tooltip and icon
 * outside formidable-engine, refusing a value that contains one of its words.
 */
export function getBlockedWordsFieldActionNode(data: BlockedWordsFieldActionData): JahiaNode {
	return {
		name: data.name || 'blockedWords',
		primaryNodeType: 'fmdbsample:blockedWordsAction',
		mixins: ['fmdbmix:fieldActionFeedback'],
		properties: [{name: 'words', values: data.words}, ...feedbackProperties(data)]
	};
}

/**
 * The samples module's field action written in JavaScript (fmdbsample:minimumWordsAction,
 * formidable-test-module-samples-tsx): a `hidden.execute` view the engine renders, refusing a value
 * with fewer words than asked for. The site must have that module enabled (`enableModule`).
 */
export function getMinimumWordsFieldActionNode(data: MinimumWordsFieldActionData = {}): JahiaNode {
	return {
		name: data.name || 'minimumWords',
		primaryNodeType: 'fmdbsample:minimumWordsAction',
		mixins: ['fmdbmix:fieldActionFeedback'],
		properties: [
			...(data.minimumWords === undefined ? [] : [{name: 'minimumWords', value: String(data.minimumWords), type: 'LONG' as const}]),
			...feedbackProperties(data)
		]
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

/** A field action behind a provider: the id of one the administrator declared, or one of the samples' doubles behind the development switch. */
export interface ProviderFieldActionData extends FieldActionFeedbackData {
	/** The id of a provider the administrator declared (fieldActionProviders, or devFieldActionProviders behind its switch). */
	providerId: string;
}

/**
 * The samples module's example implementation against a provider (fmdbsample:experianEmailAction,
 * formidable-test-module-samples-java): the address is posted to Experian Email Validation — or to the samples'
 * own double of it, ExperianStubServlet, declared as a development provider — and the confidence is the verdict.
 */
export function getExperianEmailFieldActionNode(data: ProviderFieldActionData): JahiaNode {
	return {
		name: data.name || 'experianEmail',
		primaryNodeType: 'fmdbsample:experianEmailAction',
		mixins: ['fmdbmix:fieldActionFeedback'],
		properties: [{name: 'providerId', value: data.providerId}, ...feedbackProperties(data)]
	};
}

/**
 * The samples module's second example against a provider (fmdbsample:zeroBounceEmailAction): the same engine base as
 * the Experian one, ZeroBounce's vocabulary — or the samples' double of it, ZeroBounceStubServlet, declared as a
 * development provider whose key goes on the URL.
 */
export function getZeroBounceEmailFieldActionNode(data: ProviderFieldActionData): JahiaNode {
	return {
		name: data.name || 'zeroBounceEmail',
		primaryNodeType: 'fmdbsample:zeroBounceEmailAction',
		mixins: ['fmdbmix:fieldActionFeedback'],
		properties: [{name: 'providerId', value: data.providerId}, ...feedbackProperties(data)]
	};
}
