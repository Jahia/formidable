import type { JCRNodeWrapper } from "org.jahia.services.content";

/**
 * Null-safe JCR property readers with explicit default values (TS port of the engine's JcrProps).
 * i18n properties may be absent in the session locale; every failure path returns the default.
 */

export const jcrString = (node: JCRNodeWrapper, name: string, defaultValue: string): string => {
	try {
		return node.hasProperty(name) ? node.getProperty(name).getString() : defaultValue;
	} catch {
		return defaultValue;
	}
};

export const jcrBool = (node: JCRNodeWrapper, name: string, defaultValue: boolean): boolean => {
	try {
		return node.hasProperty(name) ? node.getProperty(name).getBoolean() : defaultValue;
	} catch {
		return defaultValue;
	}
};

export const jcrLong = (node: JCRNodeWrapper, name: string, defaultValue: number): number => {
	try {
		return node.hasProperty(name) ? Number(node.getProperty(name).getLong()) : defaultValue;
	} catch {
		return defaultValue;
	}
};
