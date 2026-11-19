/** What the read-only check reads on a node: a subset of the JCR node wrapper, so that a test hands it a stub. */
export interface ActionNodeLike {
	isNodeType(name: string): boolean;
	hasNode(name: string): boolean;
	getNode(name: string): ActionNodeLike;
	getNodes(): Iterable<ActionNodeLike>;
	getPath(): string;
}

// Absence of the mixin = the action is presumed to write to the repository (the
// declaration is inverted on purpose; see the engine CND). The submission pipeline
// reads the same mixin on the same nodes, so render and submit cannot disagree.
const READ_ONLY_COMPATIBLE_ACTION_MIXIN = "fmdbmix:readOnlyCompatibleAction";

// The switch of a form that holds its uploads in quarantine. While it is on, the pipeline presumes the form writes,
// whatever its actions: the hold stores a submission with an upload, and the view cannot know whether one comes.
const QUARANTINE_MIXIN = "fmdbmix:quarantine";

/**
 * Whether the form is presumed to write to the repository, as the submission pipeline decides it in read-only
 * maintenance (FMDB-014): the switch is on, or an action is presumed to write. The form view then shows the
 * maintenance message in place of the form.
 */
export const hasRepositoryWritingAction = (formNode: ActionNodeLike): boolean => {
	try {
		if (formNode.isNodeType(QUARANTINE_MIXIN)) {
			return true;
		}
		return formNode.hasNode("actions")
			&& Array.from(formNode.getNode("actions").getNodes())
				.some((action) => !action.isNodeType(READ_ONLY_COMPATIBLE_ACTION_MIXIN));
	} catch (error) {
		console.error(`[Formidable] Could not inspect the actions of form ${formNode.getPath()}`, error);
		// Fail-closed on the scoping side: an unreadable action list is presumed writing.
		return true;
	}
};
