import {JahiaNode} from './types';

/** The module of the reference hold: a spec of the quarantine enables it on the test site itself. */
export const QUARANTINE_MODULE_ID = 'formidable-test-module-quarantine';

/**
 * The role of the quarantine test module that grants its permission fmdbquarManageQuarantine: granted on a form, and
 * published, it lets a user retry, release, resume or reject the held submissions of that form.
 */
export const QUARANTINE_MANAGER_ROLE = 'fmdbquar-quarantine-manager';

/**
 * The stub of a remote check of the quarantine test module, for the "Checks" list of a form (the `qualifiers` option
 * of createFormNode), with the CND defaults of its settings (review, retry). A form whose list holds it is owned by the
 * reference hold, which stores its submissions in the quarantine tree; any other form goes to the sample hold.
 */
export function getRemoteStubCheckNode(name: string = 'remoteCheck'): JahiaNode {
	return {name, primaryNodeType: 'fmdbquar:remoteStubCheck', properties: []};
}

/**
 * The stub of an in-process check of the quarantine test module: it declares that it reads the bytes inside Jahia, so
 * the run refuses a list where it would read a file before a remote check passed it.
 */
export function getInProcessStubCheckNode(name: string = 'inProcessCheck'): JahiaNode {
	return {name, primaryNodeType: 'fmdbquar:inProcessStubCheck', properties: []};
}
