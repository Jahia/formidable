import { server } from "@jahia/javascript-modules-library";
import type { JCRNodeWrapper, JCRSessionWrapper } from "org.jahia.services.content";
import type { HttpServletRequest } from "javax.servlet.http";
import type { List, Map as JavaMap } from "java.util";

/** Typing of the org.jahia.modules.formidable.engine.api.SubmittedFile host record. */
export interface SubmittedFile {
	fieldName(): string;
	originalName(): string;
	mimeType(): string;
	/**
	 * Java byte[] — hand it back to Java APIs (e.g. FormActionSupport); iterating it from JavaScript
	 * is slow and never needed.
	 */
	data(): unknown;
}

/** Context passed to a form action handler. */
export interface FormActionContext {
	/**
	 * The JCR node holding the action configuration, read from a live system session bound to the
	 * submission locale — i18n properties resolve to the submission language directly.
	 */
	actionNode: JCRNodeWrapper;
	/** Validated text field values of the submission. */
	parameters: Record<string, string[]>;
	/** Validated uploaded files of the submission. */
	files: SubmittedFile[];
	/** Raw Java collections of the above — pass these to FormActionSupport methods. */
	javaParameters: JavaMap<string, List<string>>;
	javaFiles: List<SubmittedFile>;
	/** The submitting user's JCR session (live workspace). */
	session: JCRSessionWrapper;
	/** Escape hatch: the raw form submission request (headers, cookies). */
	request: HttpServletRequest;
}

/**
 * Failure reported by a form action handler. The HTTP status is forwarded to the submission
 * response (as the FMDB-008 pipeline failure), mirroring
 * org.jahia.modules.formidable.engine.api.FormActionException.
 */
export class FormActionError extends Error {
	readonly status: number;

	constructor(message: string, status = 500) {
		super(message);
		this.name = "FormActionError";
		this.status = status;
	}

	static badRequest = (message: string) => new FormActionError(message, 400);
	static serverError = (message: string) => new FormActionError(message, 500);
}

/** Shape of a Java FormActionException reaching JS through a host call. */
interface JavaFormActionException {
	getHttpStatus(): number;
	getMessage(): string | null;
}

const isJavaFormActionException = (error: unknown): error is JavaFormActionException =>
	typeof error === "object" &&
	error !== null &&
	typeof (error as JavaFormActionException).getHttpStatus === "function" &&
	typeof (error as JavaFormActionException).getMessage === "function";

/**
 * Registers a form action: the handler runs when a form whose action list contains a node of the
 * given type is submitted, in action-list order, after all field validation.
 *
 * ```ts
 * registerFormAction({ nodeType: "myco:slackAction" }, ({ actionNode, parameters }) => {
 * 	// ... side effect; throw FormActionError to fail the submission
 * });
 * ```
 *
 * Handlers run synchronously on the submission thread and must not return promises. A Java
 * FormAction service registered for the same node type takes precedence.
 *
 * @param declaration `nodeType` is the action node type (must extend fmdbmix:formAction).
 * @param handler Executes the action; return normally on success, throw {@link FormActionError} (or
 *   let a FormActionSupport failure propagate) to reject the submission.
 */
export const registerFormAction = (
	{ nodeType }: { nodeType: string },
	handler: (context: FormActionContext) => void,
): void => {
	server.registry.add("formidable-form-action", nodeType, {
		nodeType,
		// Raw adapter invoked by the Java bridge (formidable-engine JsFormActionDispatcher)
		// with the FormAction#execute arguments; returns {ok: true} or
		// {ok: false, status: number, message: string}. Keep both shapes in sync.
		execute: (
			actionNode: JCRNodeWrapper,
			request: HttpServletRequest,
			session: JCRSessionWrapper,
			javaParameters: JavaMap<string, List<string>>,
			javaFiles: List<SubmittedFile>,
		) => {
			try {
				handler({
					actionNode,
					parameters: toJsParameters(javaParameters),
					files: toJsArray(javaFiles),
					javaParameters,
					javaFiles,
					session,
					request,
				});
				return { ok: true };
			} catch (error) {
				if (error instanceof FormActionError) {
					return { ok: false, status: error.status, message: error.message };
				}
				if (isJavaFormActionException(error)) {
					// e.g. a FormActionSupport call that failed: keep its HTTP status.
					return {
						ok: false,
						status: error.getHttpStatus(),
						message: error.getMessage() ?? "Form action failed.",
					};
				}
				return {
					ok: false,
					status: 500,
					message: error instanceof Error ? error.message : String(error),
				};
			}
		},
	});
	console.debug(`Registered form action for node type: ${nodeType}`);
};

/** Converts the Java Map<String, List<String>> of field values into a plain JS object. */
const toJsParameters = (
	javaParameters: JavaMap<string, List<string>>,
): Record<string, string[]> => {
	const parameters: Record<string, string[]> = {};
	if (javaParameters) {
		// keySet() is not part of the generated Map typing but is available at runtime
		const keys = (javaParameters as unknown as { keySet(): { iterator(): JavaIterator<string> } })
			.keySet()
			.iterator();
		while (keys.hasNext()) {
			const key = keys.next();
			parameters[key] = toJsArray(javaParameters.get(key));
		}
	}
	return parameters;
};

/** Copies a java.util.List into a JS array (empty for null lists). */
const toJsArray = <T>(javaList: List<T> | null): T[] => {
	const values: T[] = [];
	if (javaList) {
		for (let i = 0; i < javaList.size(); i++) {
			values.push(javaList.get(i));
		}
	}
	return values;
};

/** Minimal typing of a java.util.Iterator, which is not part of the generated Map typing. */
interface JavaIterator<T> {
	hasNext(): boolean;
	next(): T;
}
