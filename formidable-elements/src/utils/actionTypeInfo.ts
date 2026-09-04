import {server} from "@jahia/javascript-modules-library";
import type {JCRNodeWrapper} from "org.jahia.services.content";
import type {RenderContext} from "org.jahia.services.render";
import type {Locale} from "java.util";

/** What the authoring zone shows about an action's TYPE: label, description, icon. */
export interface ActionTypeInfo {
	/** Node type name, e.g. fmdb:emailNotificationAction. */
	name: string;
	/** Type label in the render locale (the Content Editor's name for the type). */
	label: string;
	/** Type-level tooltip of the Content Editor (`<type>.ui.tooltip`), plain text, if declared. */
	description?: string;
	/** URL of the type icon the module declares (the one the Content Editor shows). */
	iconUrl: string;
}

// The Java side of the node type: the library types only the javax.jcr surface, and
// not even getPrimaryNodeType() on the node.
interface ExtendedNodeTypeLike {
	getName(): string;
	getSystemId(): string;
	getLabel(locale: Locale): string;
}

const primaryNodeTypeOf = (node: JCRNodeWrapper): ExtendedNodeTypeLike =>
	(node as unknown as {getPrimaryNodeType(): ExtendedNodeTypeLike}).getPrimaryNodeType();

// Property names the engine's action types expose as their one telling parameter.
// A type absent from this map (a third-party action) shows no detail: the zone must
// never guess at a property.
const KEY_DETAIL_PROPERTY: Record<string, string> = {
	"fmdb:emailNotificationAction": "to",
	"fmdb:emailContentAction": "to",
	"fmdb:forwardAction": "targetId",
};

// Where a module keeps the resource bundle its node type labels are read from: a Java
// module under resources/, a JavaScript module under settings/resources/.
const bundlePaths = (moduleId: string, language: string): string[] => [
	`resources/${moduleId}_${language}.properties`,
	`resources/${moduleId}.properties`,
	`settings/resources/${moduleId}_${language}.properties`,
	`settings/resources/${moduleId}.properties`,
];

const readBundleValue = (moduleId: string, language: string, key: string): string | undefined => {
	const bundle = server.osgi.getBundle(moduleId);
	if (!bundle) return undefined;
	for (const path of bundlePaths(moduleId, language)) {
		try {
			const properties = server.osgi.loadPropertiesResource(bundle, path);
			// A Java Map reaches the script as a host object: read it through get().
			const value = properties && typeof properties.get === "function"
				? properties.get(key)
				: properties?.[key];
			if (value) return String(value);
		} catch {
			// Not in this file (or no such file): try the next candidate.
		}
	}
	return undefined;
};

// The tooltip is Content Editor rich text (light formatting allowed); the zone shows it
// as a single plain line, so tags are dropped.
const stripTags = (html: string): string => html.replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();

/**
 * URL of the icon a module declares for a node's primary type — the one jContent shows
 * in the tree, on the Page Builder boxes and in the type chooser: `<module>/icons/<type>.png`,
 * for a Java module (resources/icons) as for a JavaScript one (settings/content-types-icons).
 * Reusing it keeps one icon per type across every surface.
 */
export const nodeTypeIconUrl = (node: JCRNodeWrapper, renderContext: RenderContext): string => {
	const nodeType = primaryNodeTypeOf(node);
	const contextPath = renderContext.getRequest().getContextPath();
	return `${contextPath}/modules/${nodeType.getSystemId()}/icons/${nodeType.getName().replace(":", "_")}.png`;
};

/**
 * Describes the primary type of an action node from what its module already declares
 * for the Content Editor: the type label, the type-level tooltip and the type icon.
 * Nothing is duplicated in this module — a third-party action documented for the editor
 * is documented for the zone.
 */
export const describeActionType = (node: JCRNodeWrapper, renderContext: RenderContext): ActionTypeInfo => {
	const nodeType = primaryNodeTypeOf(node);
	const name = nodeType.getName();
	const moduleId = nodeType.getSystemId();
	const bundleKey = name.replace(":", "_");
	const locale = renderContext.getMainResourceLocale();

	let description: string | undefined;
	try {
		const tooltip = readBundleValue(moduleId, locale.getLanguage(), `${bundleKey}.ui.tooltip`);
		description = tooltip ? stripTags(tooltip) : undefined;
	} catch (error) {
		console.warn(`[Formidable] Could not read the tooltip of action type ${name}`, error);
	}

	return {
		name,
		label: nodeType.getLabel(locale),
		description,
		iconUrl: nodeTypeIconUrl(node, renderContext),
	};
};

/** The one contributor-set parameter worth showing next to the title (recipient, target). */
export const actionKeyDetail = (node: JCRNodeWrapper, typeName: string): string | undefined => {
	const property = KEY_DETAIL_PROPERTY[typeName];
	if (!property) return undefined;
	try {
		return node.hasProperty(property) ? node.getProperty(property).getString() || undefined : undefined;
	} catch {
		return undefined;
	}
};
