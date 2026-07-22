import { server } from "@jahia/javascript-modules-library";
import type { JCRNodeWrapper, JCRSessionWrapper } from "org.jahia.services.content";
import type { HttpServletRequest } from "javax.servlet.http";
import type { List, Map as JavaMap } from "java.util";

/**
 * Sample TypeScript form actions, registered through the RAW "formidable-form-action"
 * registry contract (deliberately not using formidable-elements' registerFormAction
 * wrapper) so the wire protocol consumed by formidable-engine's JsFormActionDispatcher
 * is exercised end-to-end:
 *
 * - entry: { nodeType, execute }
 * - execute(actionNode, request, session, parameters, files) →
 *   { ok: true } | { ok: false, status: number, message: string }
 *
 * The TypeScript counterpart of the Java sample LogSubmissionFormAction.
 */

type SubmittedFileHost = { fieldName(): string; originalName(): string };

/** Logs the submission and succeeds — the TS twin of fmdbsample:logSubmissionAction. */
server.registry.add("formidable-form-action", "fmdbsampletsx:logSubmissionTsAction", {
	nodeType: "fmdbsampletsx:logSubmissionTsAction",
	execute: (
		actionNode: JCRNodeWrapper,
		_request: HttpServletRequest,
		_session: JCRSessionWrapper,
		parameters: JavaMap<string, List<string>>,
		files: List<SubmittedFileHost>,
	) => {
		console.info(
			`[logSubmissionTsAction] node='${actionNode.getPath()}' ` +
				`parameters=${parameters.size()} files=${files.size()}`,
		);
		return { ok: true };
	},
});

/** Always fails with a client-style status, to assert the failure decoding end-to-end. */
server.registry.add("formidable-form-action", "fmdbsampletsx:alwaysFailTsAction", {
	nodeType: "fmdbsampletsx:alwaysFailTsAction",
	execute: () => ({
		ok: false,
		status: 422,
		message: "alwaysFailTsAction rejected this submission (sample).",
	}),
});
