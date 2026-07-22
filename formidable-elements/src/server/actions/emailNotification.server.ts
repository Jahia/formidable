import { server } from "@jahia/javascript-modules-library";
import { FormActionError, registerFormAction } from "../registerFormAction";
import { escapeHtml, headerSafe, plainText } from "../lib/escape";
import { interpolate } from "../lib/interpolate";
import { jcrString } from "../lib/jcr";

/**
 * Sends a notification email after a successful form submission.
 *
 * The contributor configures on the fmdb:emailNotificationAction node:
 *
 * - `to` – recipient address
 * - `from` – optional sender override
 * - `subject` – email subject (supports ${fieldName} interpolation)
 * - `templateMessage` – HTML body (supports ${fieldName} interpolation)
 *
 * Requires Jahia's MailService to be configured (SMTP settings in Jahia administration).
 */
registerFormAction({ nodeType: "fmdb:emailNotificationAction" }, ({ actionNode, parameters }) => {
	const mailService = server.osgi.getService("org.jahia.services.mail.MailService");
	if (!mailService || !mailService.isEnabled()) {
		throw FormActionError.serverError(
			"MailService is unavailable or disabled. Check Jahia SMTP configuration.",
		);
	}

	const to = headerSafe(jcrString(actionNode, "to", ""));
	if (to === "") {
		throw FormActionError.serverError("fmdb:emailNotificationAction is missing a 'to' address.");
	}

	const from = headerSafe(jcrString(actionNode, "from", ""));
	// Subject and body resolve in the submission locale (localized system session).
	const subject = headerSafe(
		interpolate(jcrString(actionNode, "subject", ""), parameters, plainText),
	);
	const htmlBody = interpolate(
		jcrString(actionNode, "templateMessage", ""),
		parameters,
		escapeHtml,
	);

	const message = new (Java.type("org.jahia.services.mail.MailMessage"))();
	message.setTo(to);
	message.setFrom(from === "" ? null : from);
	message.setSubject(subject);
	message.setHtmlBody(htmlBody);

	try {
		// Note: Jahia's MailService.sendMessage() queues the message through a Camel route.
		// SMTP delivery failures on the asynchronous delivery path are logged by Jahia/Camel
		// and do not propagate back to this call site. The catch below only handles
		// synchronous failures raised while invoking the mail service.
		mailService.sendMessage(message);
	} catch (error) {
		throw FormActionError.serverError(
			`Failed to send email notification to '${to}': ${error instanceof Error ? error.message : error}`,
		);
	}
});
