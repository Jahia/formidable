// Local copy of the formidable-elements validation contract.
// The Form client reads these data attributes to display inline error messages;
// external modules must emit the same attributes to participate.
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
