import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "fullPage",
		displayName: "Full Page",
	},
	(_, {currentNode}) => (
		<Render node={currentNode}/>
	),
);
