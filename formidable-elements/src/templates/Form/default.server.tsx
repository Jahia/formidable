import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {Layout} from "~/templates/Layout";

/**
 * Template for fmdb:form nodes. Makes a form displayable as a standalone page,
 * which is what allows jContent to open it in Page Builder for inline editing.
 */
jahiaComponent(
	{
		nodeType: "fmdb:form",
		name: "default",
		componentType: "template",
		priority: 1,
	},
	(_, {currentNode}) => (
		<Layout className="fmdb-form-page">
			<Render node={currentNode} view="fullPage"/>
		</Layout>
	),
);
