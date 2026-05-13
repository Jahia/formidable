import {jahiaComponent, Render} from "@jahia/javascript-modules-library";

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:formReference",
		name: "cm",
		displayName: "jContent internal view",
	},
	(_, {currentNode}) => (
		<Render node={currentNode}/>
	),
);
