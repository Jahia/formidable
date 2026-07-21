// Local copy of the formidable-elements HelpText contract: same id scheme and
// fmdb-form-help class so the validation client and theme CSS treat external
// fields exactly like built-in ones.
export interface HelpTextProps {
  id?: string;
  text?: string;
}

export const helpTextId = (nodeId: string) => `help-${nodeId}`;

// text is contributor-authored rich text — same trust model as fmdb:richText.
export default function HelpText({ id, text }: HelpTextProps) {
  if (!text) {
    return null;
  }

  return <div id={id} className="fmdb-form-help" dangerouslySetInnerHTML={{ __html: text }} />;
}
