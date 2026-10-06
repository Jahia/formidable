/**
 * The crossed-out eye of a hidden field's authoring line (Lucide-inspired, no icon library): the
 * field exists in the form but is never shown to the visitor.
 */
const EyeOffIcon = () => (
	<svg className="fmdb-authoring-hidden-field-glyph" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
		<path d="M10.7 5.1A9.8 9.8 0 0 1 12 5c6 0 9.5 7 9.5 7a17 17 0 0 1-2.2 3.2"/>
		<path d="M6.6 6.6C3.9 8.3 2.5 12 2.5 12S6 19 12 19a9.4 9.4 0 0 0 5.4-1.6"/>
		<path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>
		<line x1="2" y1="2" x2="22" y2="22"/>
	</svg>
);

export default EyeOffIcon;
