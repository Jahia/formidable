/**
 * What the element wrapper needs to know about a field's actions, read from the node at render
 * time (docs/architecture/field-actions.md, "The browser"). Kept free of the modules library so a
 * fake node drives the unit test.
 */

/** The javax.jcr surface read here; a JCRNodeWrapper satisfies it. */
export interface FieldActionNodeLike {
	isNodeType(name: string): boolean;
	hasNode(name: string): boolean;
	getNode(name: string): FieldActionNodeLike;
	getNodes(): Iterable<FieldActionNodeLike>;
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

const FIELD_ACTIONS_MIXIN = "fmdbmix:fieldActions";
const FIELD_ACTION_MIXIN = "fmdbmix:fieldAction";
const ACTIONS_LIST = "actions";

export const fieldActionsOf = (node: FieldActionNodeLike): FieldActionsOfNode => {
	if (!node.isNodeType(FIELD_ACTIONS_MIXIN) || !node.hasNode(ACTIONS_LIST)) {
		return {hasList: false};
	}
	let trigger: FieldActionTrigger | undefined;
	for (const action of Array.from(node.getNode(ACTIONS_LIST).getNodes())) {
		if (!action.isNodeType(FIELD_ACTION_MIXIN)) {
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
