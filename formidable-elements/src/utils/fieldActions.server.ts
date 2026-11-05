/**
 * What the element wrapper needs to know about a field's actions, read from the node at render
 * time (docs/architecture/field-actions.md, "The browser"). Kept free of the modules library so a
 * fake node drives the unit test.
 */

import type {JCRNodeWrapper} from "org.jahia.services.content";
import {SENSITIVE_FIELD_MIXIN} from "~/utils/sensitiveFields";

/** The javax.jcr surface read here; a JCRNodeWrapper satisfies it. */
export interface FieldActionNodeLike {
	isNodeType(name: string): boolean;
	hasNode(name: string): boolean;
	getNode(name: string): FieldActionNodeLike;
	getNodes(): Iterable<FieldActionNodeLike>;
	getPrimaryNodeTypeName(): string;
	hasProperty(name: string): boolean;
	getProperty(name: string): {getString(): string};
}

/** When the browser asks: as the visitor leaves the field, or at submission only. */
export type FieldActionTrigger = "blur" | "submit";

export interface FieldActionsOfNode {
	/** The switch is on: the `actions` list exists (the edit-mode zone renders, empty or not). */
	hasList: boolean;
	/**
	 * The value of the wrapper's `data-fmdb-field-action` marker: `blur` if any action of the list is
	 * checked as the visitor leaves the field — the CND default when the property is absent —, `submit`
	 * when every one waits for the submission; undefined when the list is empty or absent, so no marker
	 * is rendered and the client leaves the field alone.
	 */
	trigger?: FieldActionTrigger;
}

/**
 * Whether the Java field action registered for a field-action node type consumes sensitive values: the engine's
 * `FieldActionTypes#consumesSensitiveValues`, asked by the caller. False when the engine cannot tell.
 */
export type SensitiveValueConsumers = (fieldActionNodeType: string) => boolean;

export const FIELD_ACTION_TYPES_SERVICE = "org.jahia.modules.formidable.engine.api.FieldActionTypes";

// The engine's service, reached over OSGi; offeredUnder answers a java.util.List.
export interface FieldActionTypesLike {
	offeredUnder(actionList: JCRNodeWrapper): {isEmpty(): boolean};
	consumesSensitiveValues(fieldActionNodeType: string): boolean;
}

/**
 * The engine's answer, read through `getService` (the modules library's `server.osgi.getService`, passed in so that
 * this module stays free of it). Fails closed: without the service, or when it throws, no field action consumes
 * sensitive values, and the browser never sends a password to the pre-check.
 *
 * Build one per field, as `fieldActionsOf` is called: the service is looked up at the first question only, never for
 * a field that is not sensitive, and a missing service is reported once.
 */
export const sensitiveValueConsumersOf = (getService: (name: string) => unknown): SensitiveValueConsumers => {
	// undefined until the first question; null once the lookup found nothing or threw
	let service: FieldActionTypesLike | null | undefined;
	const lookUp = (): FieldActionTypesLike | null => {
		try {
			const found = getService(FIELD_ACTION_TYPES_SERVICE) as FieldActionTypesLike | null | undefined;
			if (!found) {
				console.warn("[Formidable] The engine's FieldActionTypes service is not available: no field action runs on a sensitive field");
			}
			return found ?? null;
		} catch (error) {
			console.error("[Formidable] Could not reach the engine's FieldActionTypes service: no field action runs on a sensitive field", error);
			return null;
		}
	};
	return fieldActionNodeType => {
		if (service === undefined) {
			service = lookUp();
		}
		if (!service) {
			return false;
		}
		try {
			return Boolean(service.consumesSensitiveValues(fieldActionNodeType));
		} catch (error) {
			console.error(`[Formidable] Could not tell whether ${fieldActionNodeType} consumes sensitive values: it does not run on a sensitive field`, error);
			return false;
		}
	};
};

const FIELD_ACTIONS_MIXIN = "fmdbmix:fieldActions";
const FIELD_ACTION_MIXIN = "fmdbmix:fieldAction";
const ACTIONS_LIST = "actions";

/**
 * @param consumesSensitiveValues asked only for a field carrying fmdbmix:sensitiveField, once per field action: the
 *                                engine runs no other action there, so the browser asks about no other. Without
 *                                it, a sensitive field gets no trigger.
 */
export const fieldActionsOf = (
	node: FieldActionNodeLike,
	consumesSensitiveValues: SensitiveValueConsumers = () => false,
): FieldActionsOfNode => {
	if (!node.isNodeType(FIELD_ACTIONS_MIXIN) || !node.hasNode(ACTIONS_LIST)) {
		return {hasList: false};
	}
	const sensitive = node.isNodeType(SENSITIVE_FIELD_MIXIN);
	let trigger: FieldActionTrigger | undefined;
	for (const action of Array.from(node.getNode(ACTIONS_LIST).getNodes())) {
		if (!action.isNodeType(FIELD_ACTION_MIXIN)) {
			continue;
		}
		// String(): the node type name is a Java string
		if (sensitive && !consumesSensitiveValues(String(action.getPrimaryNodeTypeName()))) {
			continue;
		}
		// String(): the property value is a Java string
		const declared = action.hasProperty("trigger") ? String(action.getProperty("trigger").getString()) : "blur";
		if (declared !== "submit") {
			return {hasList: true, trigger: "blur"};
		}
		trigger = "submit";
	}
	return {hasList: true, trigger};
};
