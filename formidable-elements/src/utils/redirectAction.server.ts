/**
 * What the authoring card of a redirect action (fmdb:redirectAction, docs/architecture/redirect-action.md) reads
 * from the node: where it sends the visitor, and whether an earlier redirect of the list takes precedence. Kept free
 * of the modules library so a fake node drives the unit test.
 */

import {isSameOriginPath} from "~/hooks/useFormSubmission";

export const REDIRECT_ACTION_TYPE = "fmdb:redirectAction";

/** The javax.jcr surface read here; a JCRNodeWrapper satisfies it. */
export interface RedirectActionNodeLike {
	getIdentifier(): string;
	isNodeType(name: string): boolean;
	getParent(): {getNodes(): Iterable<RedirectActionNodeLike>};
	hasProperty(name: string): boolean;
	getProperty(name: string): {getString(): string; getNode(): {getDisplayableName(): string}};
}

/**
 * True when a redirect action comes before this one in its list. The pipeline follows the first redirect whose
 * target it can use, so this one is followed only when every earlier one has no usable target.
 */
export function followsAnotherRedirect(action: RedirectActionNodeLike): boolean {
	const id = String(action.getIdentifier());
	for (const sibling of action.getParent().getNodes()) {
		if (String(sibling.getIdentifier()) === id) return false;
		if (sibling.isNodeType(REDIRECT_ACTION_TYPE)) return true;
	}
	return false;
}

/**
 * The target shown next to the card's title: the page's title, or the URL in the current language. Undefined
 * when the target is not set, or when the picked page is gone (the weak reference no longer resolves).
 */
export function redirectTargetDetail(action: RedirectActionNodeLike): string | undefined {
	// The CND default, when the property is absent
	const type = action.hasProperty("targetType") ? String(action.getProperty("targetType").getString()) : "page";
	if (type === "url") {
		const url = action.hasProperty("url") ? String(action.getProperty("url").getString()).trim() : "";
		return url || undefined;
	}
	if (!action.hasProperty("page")) return undefined;
	try {
		return String(action.getProperty("page").getNode().getDisplayableName()) || undefined;
	} catch {
		return undefined;
	}
}

/** What no redirect target may hold, as the browser's check (`useFormSubmission`) and the server's (`RedirectTargets`) read it. */
const FORBIDDEN_IN_TARGET = /[\\\p{Cc}\p{Z}\p{Cf}]/u;

/**
 * An absolute http(s) URL with a host and no user info, the rule of the browser's `isHttpUrl`, read without the `URL`
 * parser that check relies on: the card renders on the server, outside a browser.
 */
function isHttpUrlOnServer(url: string): boolean {
	if (url.length > 2048 || FORBIDDEN_IN_TARGET.test(url)) return false;
	const authority = /^https?:\/\/([^/?#]*)/i.exec(url)?.[1];
	return authority !== undefined && !authority.includes("@") && authority.replace(/:\d*$/, "") !== "";
}

/**
 * True when the action targets a URL that the redirect will not follow: neither a path of this server nor an absolute
 * http(s) URL, by the rules the browser and the server both apply. The editor's constraint refuses the commonest
 * mistake (no scheme); this catches the rest, a host-less or protocol-relative URL, user info, a forbidden character.
 * A missing URL is no warning here: the language may fall back to another one.
 */
export function hasUnusableUrl(action: RedirectActionNodeLike): boolean {
	if (!action.hasProperty("targetType") || String(action.getProperty("targetType").getString()) !== "url") return false;
	if (!action.hasProperty("url")) return false;
	const url = String(action.getProperty("url").getString()).trim();
	return url !== "" && !isSameOriginPath(url) && !isHttpUrlOnServer(url);
}
