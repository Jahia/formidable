import {JahiaNode, NodeProperty} from './types';
/**
 * Builders for the action nodes of a form (children of its 'actions' list).
 * The titles are i18n (mix:title), the recipients are not.
 */

export interface EmailNotificationActionData {
	name?: string;
	title?: string;
	to: string;
	from?: string;
	subject?: string;
	templateMessage?: string;
}

export function getEmailNotificationActionNode(data: EmailNotificationActionData): JahiaNode {
	const properties: NodeProperty[] = [{name: 'to', value: data.to}];

	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.from) properties.push({name: 'from', value: data.from});
	if (data.subject) properties.push({name: 'subject', value: data.subject, language: 'en'});
	if (data.templateMessage) properties.push({name: 'templateMessage', value: data.templateMessage, language: 'en'});

	return {
		name: data.name || 'emailNotification',
		primaryNodeType: 'fmdb:emailNotificationAction',
		properties
	};
}

export function getSaveToJcrActionNode(name: string = 'storeSubmission'): JahiaNode {
	return {
		name,
		primaryNodeType: 'fmdb:save2jcrAction',
		properties: []
	};
}

/**
 * The test module's own action (fmdbsample:logSubmissionAction, formidable-test-module-samples-java):
 * a third-party type, declared with its label, tooltip and icon outside formidable-engine.
 */
export function getLogSubmissionActionNode(name: string = 'logSubmission'): JahiaNode {
	return {
		name,
		primaryNodeType: 'fmdbsample:logSubmissionAction',
		properties: []
	};
}

/**
 * The test module's contextual action that consumes the sensitive values of the form (fmdbsample:sensitiveConsumerAction,
 * formidable-test-module-samples-java): it opts in from its Java class, and reports the number of sensitive fields it
 * received and whether the field named `password` held a non-blank value, `true` or `false`, in the response headers
 * SENSITIVE_CONSUMER_HEADERS names.
 */
export function getSensitiveConsumerActionNode(name: string = 'sensitiveConsumer'): JahiaNode {
	return {name, primaryNodeType: 'fmdbsample:sensitiveConsumerAction', properties: []};
}

/**
 * The test module's contextual action that does not opt in (fmdbsample:sensitiveNonConsumerAction): it reports the
 * number of sensitive fields it received, always 0, and the names of its public fields, in the response headers
 * SENSITIVE_NON_CONSUMER_HEADERS names.
 */
export function getSensitiveNonConsumerActionNode(name: string = 'sensitiveNonConsumer'): JahiaNode {
	return {name, primaryNodeType: 'fmdbsample:sensitiveNonConsumerAction', properties: []};
}

/** The response headers of the two sample actions, lower-cased as Cypress exposes them. Counts, yes or no, and names only. */
export const SENSITIVE_CONSUMER_HEADERS = {
	sensitiveFields: 'x-fmdb-sample-sensitive-fields',
	passwordReceived: 'x-fmdb-sample-password-received'
} as const;
export const SENSITIVE_NON_CONSUMER_HEADERS = {
	sensitiveFields: 'x-fmdb-sample-non-consumer-sensitive-fields',
	fields: 'x-fmdb-sample-non-consumer-fields'
} as const;

/**
 * A forward action pointing at a configured forward target (fmdb:forwardAction, targetId is a
 * choicelist fed by the forward target files).
 */
export function getForwardActionNode(data: {name?: string; title?: string; targetId: string}): JahiaNode {
	const properties: NodeProperty[] = [{name: 'targetId', value: data.targetId}];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});

	return {
		name: data.name || 'forwardData',
		primaryNodeType: 'fmdb:forwardAction',
		properties
	};
}

/**
 * The redirect action (fmdb:redirectAction): a page (weak reference to a page UUID) or an http(s) URL, each on the
 * mixin its targetType activates. The URL is stored as the contributor typed it: the
 * repository refuses only one that does not start with http:// or https://, and the server decides on the rest.
 * `url` is the English value; `urls` gives one value per language instead.
 */
export function getRedirectActionNode(data: {name?: string; title?: string; url?: string; urls?: Record<string, string>; pageUuid?: string}): JahiaNode {
	const properties: NodeProperty[] = [{name: 'targetType', value: data.pageUuid ? 'page' : 'url'}];
	if (data.title) properties.push({name: 'jcr:title', value: data.title, language: 'en'});
	if (data.url) properties.push({name: 'url', value: data.url, language: 'en'});
	Object.entries(data.urls ?? {}).forEach(([language, value]) => properties.push({name: 'url', value, language}));
	if (data.pageUuid) properties.push({name: 'page', value: data.pageUuid, type: 'WEAKREFERENCE'});

	return {
		name: data.name || 'redirect',
		primaryNodeType: 'fmdb:redirectAction',
		mixins: [data.pageUuid ? 'fmdbmix:redirectToPage' : 'fmdbmix:redirectToUrl'],
		properties
	};
}
