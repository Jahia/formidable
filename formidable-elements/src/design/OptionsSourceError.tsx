import {useTranslation} from "react-i18next";

interface OptionsSourceErrorProps {
	label?: string;
	required?: boolean;
}

/**
 * Degraded rendering of a sourced choice field whose source could not deliver (D10):
 * the field shows an error instead of its options. When the field is required, the
 * data attribute is read by the Form island to block submission; an optional field
 * leaves the form usable without it.
 */
export default function OptionsSourceError({label, required}: OptionsSourceErrorProps) {
	const {t} = useTranslation("formidable-elements", {keyPrefix: "fmdb_choiceField"});

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
				{required ? t("sourceUnavailableRequired") : t("sourceUnavailable")}
			</p>
		</div>
	);
}
