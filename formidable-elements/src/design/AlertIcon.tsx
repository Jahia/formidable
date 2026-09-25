/**
 * The one glyph the authoring zones draw themselves (Lucide-inspired, no icon library): the
 * triangle of a warning. Everything else they show is a type icon the platform serves.
 */
const AlertIcon = () => (
	<svg className="fmdb-authoring-actions-glyph" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
		<path d="M12 3 2 20h20L12 3z"/>
		<line x1="12" y1="9" x2="12" y2="14"/>
		<line x1="12" y1="17" x2="12.01" y2="17"/>
	</svg>
);

export default AlertIcon;
