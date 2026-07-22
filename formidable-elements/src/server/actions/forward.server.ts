import { server } from "@jahia/javascript-modules-library";
import { FormActionError, registerFormAction } from "../registerFormAction";
import { jcrString } from "../lib/jcr";

/**
 * Forwards the submitted form data to a third-party endpoint as multipart/form-data.
 *
 * Deliberately a thin shim: target resolution (targetId → URI via operator configuration), the SSRF
 * hostname checks, multipart body construction and the HTTP call all stay in Java behind
 * FormActionSupport.forwardSubmission — this handler never sees the target URL. Failures thrown by
 * the Java service keep their HTTP status (403/400/502/500).
 */
registerFormAction(
	{ nodeType: "fmdb:forwardAction" },
	({ actionNode, javaParameters, javaFiles }) => {
		const targetId = jcrString(actionNode, "targetId", "").trim();
		if (targetId === "") {
			console.warn(
				`[forwardAction] targetId is missing or blank on node '${actionNode.getPath()}', skipping.`,
			);
			return;
		}

		const support = server.osgi.getService(
			"org.jahia.modules.formidable.engine.api.FormActionSupport",
		);
		if (!support) {
			throw FormActionError.serverError("FormActionSupport service is unavailable.");
		}

		support.forwardSubmission(targetId, javaParameters, javaFiles);
	},
);
