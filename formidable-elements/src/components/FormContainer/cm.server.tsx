import {AddResources, buildModuleFileUrl, getNodeProps, jahiaComponent, Render} from "@jahia/javascript-modules-library";

type ContainerNode = Parameters<typeof getNodeProps>[0];

/**
 * Inspection view for a form container opened on its own in jContent (the field list, a
 * step, a fieldset): without it the preview drawer falls back to the engine's generic
 * rendering — the fmdb markup comes out, but the stylesheet and the form's own CSS only
 * travel with the form views, so the container renders bare. Same shell as the form's cm
 * view: the enclosing form's CSS, the module stylesheet, and logic-driven fields visible.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdbmix:formContainer",
		name: "cm",
		displayName: "jContent internal view"
	},
	(_, {currentNode}) => {
		// The css property lives on the enclosing form (the list's parent, a step's
		// grandparent, deeper for a nested fieldset).
		let formNode: ContainerNode | null = null;
		try {
			let candidate = currentNode.getParent() as ContainerNode | null;
			while (candidate && !candidate.isNodeType("fmdb:form")) {
				candidate = candidate.isNodeType("jnt:contentFolder") || candidate.isNodeType("jnt:virtualsite")
					? null
					: candidate.getParent() as ContainerNode | null;
			}

			formNode = candidate;
		} catch {
			// Reached the repository root without meeting a form: render unthemed.
		}

		const css = formNode ? getNodeProps<{css?: string}>(formNode, ["css"]).css : undefined;
		// The field list has no default view (the form renders it through hidden.logic);
		// a step or fieldset keeps its default view, titles included.
		const isFieldList = currentNode.isNodeType("fmdb:fieldList");

		return (
			<>
				{css && <style>{css}</style>}
				<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")}/>
				{/* Not a <form>: nothing here submits. The class keeps the business stylesheet's look. */}
				<div className="fmdb-form" data-fmdb-cm-view="true">
					{isFieldList
						? (
							<Render
								node={currentNode}
								view="hidden.logic"
								parameters={{childView: "default", showLogicHidden: "true"}}
							/>
						)
						: <Render node={currentNode} parameters={{showLogicHidden: "true"}}/>}
				</div>
			</>
		);
	}
);
