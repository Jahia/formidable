import {jahiaComponent} from "@jahia/javascript-modules-library";
import FieldsetBody from "./FieldsetBody";

interface FieldsetProps {
	"jcr:title"?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:fieldset",
		name: "default"
	},
	(
		{"jcr:title": title}: FieldsetProps,
		{currentNode, currentResource}
	) => {
		const showLogicHidden = currentResource.getModuleParams().get("showLogicHidden")?.toString() === "true";

		return <FieldsetBody title={title} currentNode={currentNode} showLogicHidden={showLogicHidden}/>;
	}
);
