import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:form",
		name: "cm",
		displayName: "jContent internal view",
	},
	(_, {currentNode}) => (
		<Render node={currentNode}/>
	),
);
