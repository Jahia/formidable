/**
 * What the authoring card of a redirect action (fmdb:redirectAction, docs/architecture/redirect-action.md) reads
 * from the node: where it sends the visitor, and whether an earlier redirect of the list takes precedence. Kept free
 * of the modules library so a fake node drives the unit test.
 */

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
