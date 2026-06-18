import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import clsx from "clsx";

interface FieldsetCustomStyleProps {
	"jcr:title"?: string;
	customCssClassname?: string[];
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:fieldset",
		name: "customStyle",
		displayName: "Fieldset - Custom CSS class names",
	},
	(
		{"jcr:title": title, customCssClassname}: FieldsetCustomStyleProps,
		{currentNode}
	) => {
		const elementNodes = Array.from(currentNode.getNodes());
		const extraClasses = customCssClassname?.join(" ") ?? "";

		return (
			<fieldset className={clsx("fmdb-fieldset",extraClasses)}>
				{title && (
					<legend className="fmdb-fieldset-legend">
						{title}
					</legend>
				)}

				{elementNodes.length > 0 && (
					<Render
						node={currentNode}
						view="hidden.logic"
						parameters={{
							className: "fmdb-fieldset-elements",
							childClassName: "fmdb-form-element",
						}}
					/>
				)}
			</fieldset>
		);
	},
);

