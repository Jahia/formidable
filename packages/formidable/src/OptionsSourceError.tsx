export interface OptionsSourceErrorProps {
	label?: string;
	required?: boolean;
	/** The text shown in place of the options, in the module's own words and language. */
	message: string;
}

// A choice field whose options source could not deliver shows this instead of its options. The
// Form island reads data-fmdb-source-error="blocking" to keep a required field's form from being
// submitted; an optional field leaves the form usable without it.
export function OptionsSourceError({label, required, message}: Readonly<OptionsSourceErrorProps>) {
	return (
		<div
			className="fmdb-form-group fmdb-options-source-error"
			data-fmdb-source-error={required ? "blocking" : "optional"}
		>
			{label && (
				<span className="fmdb-form-label">
					{label}
					{required && <span className="fmdb-required-indicator" aria-hidden="true">*</span>}
				</span>
			)}
			<p className="fmdb-field-error" role="alert">
				{message}
			</p>
		</div>
	);
}
