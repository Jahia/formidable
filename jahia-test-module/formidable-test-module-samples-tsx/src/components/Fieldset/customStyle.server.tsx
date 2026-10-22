import {jahiaComponent, Render} from "@jahia/javascript-modules-library";
import {HelpText, helpTextId} from "@jahia/formidable-library";
import clsx from "clsx";

interface FieldsetCustomStyleProps {
	"jcr:title"?: string;
	helpText?: string;
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
		{"jcr:title": title, helpText, customCssClassname}: FieldsetCustomStyleProps,
		{currentNode}
	) => {
		// A view of its own still renders the help of the fieldset, under the legend, and points at it
		const helpId = helpText ? helpTextId(currentNode.getIdentifier()) : undefined;
		return (
			<fieldset className={clsx("fmdb-fieldset", customCssClassname)} aria-describedby={helpId}>
				{title && (
					<legend className="fmdb-fieldset-legend">
						{title}
					</legend>
				)}
				<HelpText id={helpId} text={helpText}/>

				{/* Always rendered, children or not: hidden.logic emits the Page Builder
				    placeholder an empty fieldset needs to ever receive a field. */}
				<Render
					node={currentNode}
					view="hidden.logic"
					parameters={{
						className: "fmdb-fieldset-elements",
						childClassName: "fmdb-form-element",
					}}
				/>
			</fieldset>
		);
	},
);

