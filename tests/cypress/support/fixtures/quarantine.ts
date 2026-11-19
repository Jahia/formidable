import {JahiaNode} from './types';

/** The module of the reference hold: a spec of the quarantine enables it on the test site itself. */
export const QUARANTINE_MODULE_ID = 'formidable-test-module-quarantine';

/** The settings of a stub check; each left out keeps its CND default (review, retry). */
export interface StubCheckSettings {
	onInconclusive?: 'review' | 'reject';
	onUnavailable?: 'retry' | 'reject';
}

const settingsOf = (settings: StubCheckSettings) => [
	...(settings.onInconclusive ? [{name: 'onInconclusive', value: settings.onInconclusive}] : []),
	...(settings.onUnavailable ? [{name: 'onUnavailable', value: settings.onUnavailable}] : [])
];

/**
 * The stub of a remote check of the quarantine test module, for the "Checks" list of a form (the `qualifiers` option
 * of createFormNode). A form whose list holds it is owned by the reference hold, which stores its submissions in the
 * quarantine tree; any other form goes to the sample hold.
 */
export function getRemoteStubCheckNode(name: string = 'remoteCheck', settings: StubCheckSettings = {}): JahiaNode {
	return {name, primaryNodeType: 'fmdbquar:remoteStubCheck', properties: settingsOf(settings)};
}

/**
 * The stub of an in-process check of the quarantine test module: it declares that it reads the bytes inside Jahia, so
 * the run refuses a list where it would read a file before a remote check passed it.
 */
export function getInProcessStubCheckNode(name: string = 'inProcessCheck', settings: StubCheckSettings = {}): JahiaNode {
	return {name, primaryNodeType: 'fmdbquar:inProcessStubCheck', properties: settingsOf(settings)};
}
