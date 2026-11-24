import {JahiaNode} from './types';

/** The module of the reference hold: a spec of the quarantine enables it on the test site itself. */
export const QUARANTINE_MODULE_ID = 'formidable-test-module-quarantine';

/**
 * The stub of a remote check of the quarantine test module, for the "Checks" list of a form (the `qualifiers` option
 * of createFormNode). A form whose list holds it is owned by the reference hold, which stores its submissions in the
 * quarantine tree; any other form goes to the sample hold.
 */
export function getRemoteStubCheckNode(name: string = 'remoteCheck'): JahiaNode {
	return {name, primaryNodeType: 'fmdbquar:remoteStubCheck', properties: []};
}
