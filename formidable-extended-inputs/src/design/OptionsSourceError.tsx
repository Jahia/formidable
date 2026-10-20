import { useTranslation } from "react-i18next";

interface OptionsSourceErrorProps {
  label?: string;
  required?: boolean;
}

/**
 * Copy of formidable-elements/src/design/OptionsSourceError.tsx, with this module's bundle: a choice field
 * whose options source could not deliver shows an error instead of its options. The Form island reads
 * data-fmdb-source-error="blocking" to block the submission of a required field.
 */
export default function OptionsSourceError({ label, required }: OptionsSourceErrorProps) {
  const { t } = useTranslation("formidable-extended-inputs", { keyPrefix: "fmdbext_choiceField" });

  return (
    <div
      className="fmdb-form-group fmdb-options-source-error"
      data-fmdb-source-error={required ? "blocking" : "optional"}
    >
      {label && (
        <span className="fmdb-form-label">
          {label}
          {required && (
            <span className="fmdb-required-indicator" aria-hidden="true">
              *
            </span>
          )}
        </span>
      )}
      <p className="fmdb-field-error" role="alert">
        {required ? t("sourceUnavailableRequired") : t("sourceUnavailable")}
      </p>
    </div>
  );
}
