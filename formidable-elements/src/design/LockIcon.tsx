/**
 * The padlock of the sign-in authoring line (Lucide-inspired, no icon library): the form takes the
 * submissions of signed-in visitors only, which nothing shows in edit mode otherwise.
 */
const LockIcon = () => (
	<svg className="fmdb-authoring-hidden-field-glyph" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
		<rect x="4" y="11" width="16" height="10" rx="2"/>
		<path d="M8 11V7a4 4 0 0 1 8 0v4"/>
	</svg>
);

export default LockIcon;
