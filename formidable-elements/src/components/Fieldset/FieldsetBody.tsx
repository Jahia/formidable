import {getNodeProps, Render} from "@jahia/javascript-modules-library";
import type {CSSProperties, ReactNode} from "react";

type FieldsetNode = Parameters<typeof getNodeProps>[0];

interface FieldsetBodyProps {
	title?: string;
	currentNode: FieldsetNode;
	/** Logic-hidden elements stay visible: the cm inspection view (hidden.logic). */
	showLogicHidden: boolean;
	/** Classes beside fmdb-fieldset, and the inline style, a view adds to the fieldset. */
	className?: string;
	style?: CSSProperties;
	/** Rendered between the legend and the elements: the column headings of the matrix view. */
	header?: ReactNode;
}

/**
 * The fieldset as every view of fmdb:fieldset renders it: the legend from mix:title, then the
 * elements through the hidden.logic pass of the container (docs/styling/README.md, "What the
 * markup looks like"). A view adds its hooks around that shared body.
 */
export default function FieldsetBody({title, currentNode, showLogicHidden, className, style, header}: FieldsetBodyProps) {
	return (
		<fieldset className={className ? `fmdb-fieldset ${className}` : "fmdb-fieldset"} style={style}>
			{/* Fieldset title from mix:title */}
			{title && (
				<legend className="fmdb-fieldset-legend">
					{title}
				</legend>
			)}

			{header}

			{/* Always rendered, children or not: hidden.logic is what emits the Page
			    Builder placeholder, and an empty fieldset is exactly when the create
			    buttons are indispensable — without them nothing can ever go in. */}
			<Render
				node={currentNode}
				view="hidden.logic"
				readOnly
				parameters={{
					className: "fmdb-fieldset-elements",
					childClassName: "fmdb-form-element",
					...(showLogicHidden ? {showLogicHidden: "true"} : {}),
				}}
			/>
		</fieldset>
	);
}
