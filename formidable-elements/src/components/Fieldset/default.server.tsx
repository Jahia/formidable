import {jahiaComponent} from "@jahia/javascript-modules-library";
import FieldsetBody from "./FieldsetBody";

interface FieldsetProps {
	"jcr:title"?: string;
	helpText?: string;
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:fieldset",
		name: "default"
	},
	(
		{"jcr:title": title, helpText}: FieldsetProps,
		{currentNode, currentResource}
	) => {
		const showLogicHidden = currentResource.getModuleParams().get("showLogicHidden")?.toString() === "true";

		return <FieldsetBody title={title} helpText={helpText} currentNode={currentNode} showLogicHidden={showLogicHidden}/>;
	}
);
