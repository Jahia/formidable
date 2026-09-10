export interface HelpTextProps {
	id?: string;
	text?: string;
}

// Builds the stable id linking a control to its help text via aria-describedby.
// Static ids are preserved by the validation client (updateDescribedBy only
// adds/removes its own error id).
export const helpTextId = (nodeId: string) => `help-${nodeId}`;

// text is contributor-authored rich text (bold, italic, links) coming from a
// richtext property — rendered as HTML, same trust model as fmdb:richText.
export function HelpText({id, text}: HelpTextProps) {
	if (!text) {
		return null;
	}

	return (
		<div id={id} className="fmdb-form-help" dangerouslySetInnerHTML={{__html: text}}/>
	);
}
