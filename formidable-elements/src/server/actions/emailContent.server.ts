import { server } from "@jahia/javascript-modules-library";
import type { JCRNodeWrapper } from "org.jahia.services.content";
import { FormActionError, registerFormAction } from "../registerFormAction";
import { escapeHtml, headerSafe, plainText } from "../lib/escape";
import { jcrBool, jcrLong, jcrString } from "../lib/jcr";

const DEFAULT_SUBJECT = "Form submission";

/**
 * Sends the submitted form content by email, optionally with uploaded files attached.
 *
 * The contributor configures on the fmdb:emailContentAction node:
 *
 * - `to` – recipient address
 * - `from` – optional sender override
 * - `attachFiles` – whether validated uploaded files should be attached
 * - `maxAttachmentSizeMb` – per-attachment size cap for this email action
 *
 * The email subject is derived from the submitted form title. Attachment building (RFC 6266 names,
 * size caps) stays in Java behind FormActionSupport.
 */
registerFormAction(
	{ nodeType: "fmdb:emailContentAction" },
	({ actionNode, parameters, javaFiles }) => {
		const mailService = server.osgi.getService("org.jahia.services.mail.MailService");
		if (!mailService || !mailService.isEnabled()) {
			throw FormActionError.serverError(
				"MailService is unavailable or disabled. Check Jahia SMTP configuration.",
			);
		}

		const to = headerSafe(jcrString(actionNode, "to", ""));
		if (to === "") {
			throw FormActionError.serverError("fmdb:emailContentAction is missing a 'to' address.");
		}

		const from = headerSafe(jcrString(actionNode, "from", ""));
		const subject = headerSafe(resolveFormSubject(actionNode));

		const message = new (Java.type("org.jahia.services.mail.MailMessage"))();
		message.setTo(to);
		message.setFrom(from === "" ? null : from);
		message.setSubject(subject === "" ? DEFAULT_SUBJECT : subject);
		message.setTextBody(buildTextBody(subject, parameters));
		message.setHtmlBody(buildHtmlBody(subject, parameters));

		if (jcrBool(actionNode, "attachFiles", false)) {
			const support = server.osgi.getService(
				"org.jahia.modules.formidable.engine.api.FormActionSupport",
			);
			if (!support) {
				throw FormActionError.serverError("FormActionSupport service is unavailable.");
			}
			const configuredMb = Math.max(1, jcrLong(actionNode, "maxAttachmentSizeMb", 10));
			message.setAttachments(support.buildEmailAttachments(javaFiles, configuredMb * 1024 * 1024));
		}

		try {
			// Note: Jahia's MailService.sendMessage() queues the message through a Camel route.
			// SMTP delivery failures on the asynchronous delivery path are logged by Jahia/Camel
			// and do not propagate back to this call site. The catch below only handles
			// synchronous failures raised while invoking the mail service.
			mailService.sendMessage(message);
		} catch (error) {
			throw FormActionError.serverError(
				`Failed to send form content email to '${to}': ${error instanceof Error ? error.message : error}`,
			);
		}
	},
);

/** The subject is the form node title (grandparent of the action node), with fallbacks. */
const resolveFormSubject = (actionNode: JCRNodeWrapper): string => {
	try {
		// getParent() is typed as javax.jcr.Node but returns a JCRNodeWrapper at runtime.
		const formNode = actionNode.getParent()?.getParent() as JCRNodeWrapper | undefined;
		if (!formNode) {
			return DEFAULT_SUBJECT;
		}

		const title = jcrString(formNode, "jcr:title", "");
		if (title.trim() !== "") {
			return title;
		}

		const displayableName = formNode.getDisplayableName();
		if (displayableName && displayableName.trim() !== "") {
			return displayableName;
		}
	} catch {
		// Fall through to the default subject.
	}
	return DEFAULT_SUBJECT;
};

const buildTextBody = (subject: string, parameters: Record<string, string[]>): string => {
	let body = subject.trim() !== "" ? `${subject}\n\n` : "";
	for (const [name, values] of Object.entries(parameters)) {
		body += `${name}: ${values.map(plainText).join(", ")}\n`;
	}
	return body;
};

const buildHtmlBody = (subject: string, parameters: Record<string, string[]>): string => {
	let body = "<html><body>";
	body += `<h2>${escapeHtml(subject)}</h2>`;
	body += '<table border="1" cellspacing="0" cellpadding="6">';
	for (const [name, values] of Object.entries(parameters)) {
		body += `<tr><th align="left">${escapeHtml(name)}</th><td>${values.map(escapeHtml).join("<br/>")}</td></tr>`;
	}
	body += "</table></body></html>";
	return body;
};
