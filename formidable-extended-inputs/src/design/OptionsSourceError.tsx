import { useTranslation } from "react-i18next";
import { OptionsSourceError as OptionsSourceErrorBlock } from "@jahia/formidable-library";

interface OptionsSourceErrorProps {
  label?: string;
  required?: boolean;
}

/** The library's error block of a choice field whose options source could not deliver, in this module's words. */
export default function OptionsSourceError({ label, required }: Readonly<OptionsSourceErrorProps>) {
  const { t } = useTranslation("formidable-extended-inputs", { keyPrefix: "fmdbext_choiceField" });

  return (
    <OptionsSourceErrorBlock
      label={label}
      required={required}
      message={required ? t("sourceUnavailableRequired") : t("sourceUnavailable")}
    />
  );
}
