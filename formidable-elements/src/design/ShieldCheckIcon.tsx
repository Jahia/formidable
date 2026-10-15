/**
 * The checked shield of a captcha's authoring line (Lucide-inspired, no icon library): the form asks
 * the visitor to prove they are human, outside edit mode.
 */
const ShieldCheckIcon = () => (
	<svg className="fmdb-authoring-hidden-field-glyph" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
		<path d="M12 22s8-3.5 8-10V5l-8-3-8 3v7c0 6.5 8 10 8 10z"/>
		<path d="m9 12 2 2 4-4"/>
	</svg>
);

export default ShieldCheckIcon;
