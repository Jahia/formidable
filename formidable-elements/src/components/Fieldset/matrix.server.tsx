import {getNodeProps, jahiaComponent} from "@jahia/javascript-modules-library";
import type {CSSProperties} from "react";
import {resolveFieldOptions} from "~/utils/optionsSource.server";
import FieldsetBody from "./FieldsetBody";

interface FieldsetProps {
	"jcr:title"?: string;
}

type FieldsetNode = Parameters<typeof getNodeProps>[0];
type MatrixRenderContext = Parameters<typeof resolveFieldOptions>[2];

/** The field types a matrix lays out: a group of options per row, the same options on every row. */
const ROW_TYPES = ["fmdb:radio", "fmdb:checkbox"];

/**
 * The option labels the rows of the fieldset share, in order; null when the fieldset is not a
 * matrix: no row, a row that is not a radio or checkbox group of at least two options (a single
 * option renders without a group), rows whose options differ, or an options source that fails.
 */
function columnsOf(fieldset: FieldsetNode, renderContext: MatrixRenderContext): string[] | null {
	const rows = (Array.from(fieldset.getNodes()) as FieldsetNode[]).filter(node => !node.isNodeType("fmdb:logicList"));
	if (rows.length === 0) {
		return null;
	}
	let columns: string[] | null = null;
	for (const row of rows) {
		if (!ROW_TYPES.some(type => row.isNodeType(type))) {
			return null;
		}
		const manualOptions = getNodeProps<{options?: string[]}>(row, ["options"]).options ?? [];
		const {choices, sourceError} = resolveFieldOptions(row, manualOptions, renderContext);
		const labels = choices.map(choice => choice.label);
		if (sourceError || labels.length < 2 || (columns && !sameLabels(columns, labels))) {
			return null;
		}
		columns ??= labels;
	}
	return columns;
}

const sameLabels = (a: string[], b: string[]): boolean => a.length === b.length && a.every((label, index) => label === b[index]);

/** The labels with a key each, distinct even when two options read the same. */
function keyed(labels: string[]): Array<[string, string]> {
	const seen = new Map<string, number>();
	return labels.map(label => {
		const times = seen.get(label) ?? 0;
		seen.set(label, times + 1);
		return [times === 0 ? label : `${label}#${times}`, label];
	});
}

/**
 * A fieldset of radio or checkbox groups laid out as a matrix (docs/styling/README.md, "The
 * matrix view of a fieldset"): the questions down the first column, the options across, one
 * heading row above. The rows keep the stock markup of a group, so a screen reader meets each
 * question as a group and each option by its label; only the layout, in matrix.css, changes.
 * A fieldset whose elements cannot form a matrix renders as the default view does.
 */
jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:fieldset",
		name: "matrix",
		displayName: "Matrix"
	},
	(
		{"jcr:title": title}: FieldsetProps,
		{currentNode, currentResource, renderContext}
	) => {
		const showLogicHidden = currentResource.getModuleParams().get("showLogicHidden")?.toString() === "true";
		const columns = columnsOf(currentNode, renderContext);
		if (!columns) {
			return <FieldsetBody title={title} currentNode={currentNode} showLogicHidden={showLogicHidden}/>;
		}

		// decorative: every input is labelled by its own option label and grouped under its question
		const header = (
			<div className="fmdb-matrix-header" aria-hidden="true">
				<span className="fmdb-matrix-corner"/>
				{keyed(columns).map(([key, label]) => (
					<span key={key} className="fmdb-matrix-column">{label}</span>
				))}
			</div>
		);
		const style = {"--fmdb-matrix-columns": String(columns.length)} as CSSProperties;

		return (
			<FieldsetBody
				title={title}
				currentNode={currentNode}
				showLogicHidden={showLogicHidden}
				className="fmdb-matrix"
				style={style}
				header={header}
			/>
		);
	}
);
