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

const FORMIDABLE_CONFIG_SERVICE = "org.jahia.modules.formidable.engine.config.FormidableConfigService";

const stringProperty = (node: JCRNodeWrapper, name: string): string | undefined => {
	try {
		return node.hasProperty(name) ? node.getProperty(name).getString() || undefined : undefined;
	} catch {
		return undefined;
	}
};

// A forward target is stored by its stable id (targetId); the contributor picked it by
// the label the administrator configured (the choicelist shows labels, the same way the
// Content Editor does). Show that label; fall back to the id when the target is no
// longer configured, which is worth seeing too.
const forwardTargetLabel = (targetId: string): string => {
	try {
		const service = server.osgi.getService(FORMIDABLE_CONFIG_SERVICE);
		const target = service.resolveForwardTarget(targetId);
		if (target && target.isPresent()) {
			return String(target.get().label()) || targetId;
		}
	} catch (error) {
		console.warn(`[Formidable] Could not resolve the forward target '${targetId}'`, error);
	}
	return targetId;
};

// The one telling parameter of each of the engine's action types, as the contributor
// sees it in the editor. A type absent from this map (a third-party action) shows no
// detail: the zone must never guess at a property.
const KEY_DETAIL: Record<string, (node: JCRNodeWrapper) => string | undefined> = {
	"fmdb:emailNotificationAction": (node) => stringProperty(node, "to"),
	"fmdb:emailContentAction": (node) => stringProperty(node, "to"),
	"fmdb:forwardAction": (node) => {
		const targetId = stringProperty(node, "targetId");
		return targetId ? forwardTargetLabel(targetId) : undefined;
	},
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

/** The one contributor-set parameter worth showing next to the title (recipient, target label). */
export const actionKeyDetail = (node: JCRNodeWrapper, typeName: string): string | undefined =>
	KEY_DETAIL[typeName]?.(node);
