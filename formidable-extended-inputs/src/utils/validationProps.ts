// Deliberately minimal subset of the formidable-elements validation contract
// (data-fmdb-msg-* attributes read by the Form client to display inline errors):
// these fields only use required-field messages today. Sharing the full contract
// through a common package is tracked in https://github.com/Jahia/formidable/issues/170.
export interface BaseValidationMessageProps {
  msgValueMissing?: string;
}

export const validationDataAttributes = (
  props: BaseValidationMessageProps,
): Record<string, string | undefined> => {
  const attrs: Record<string, string | undefined> = {};
  if ("msgValueMissing" in props)
    attrs["data-fmdb-msg-value-missing"] = props.msgValueMissing || undefined;
  return attrs;
};
