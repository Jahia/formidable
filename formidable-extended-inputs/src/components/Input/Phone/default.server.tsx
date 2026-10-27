import { jahiaComponent } from "@jahia/javascript-modules-library";
import { type TextValidationMessageProps, validationDataAttributes } from "@jahia/formidable-library";
import { HelpText, helpTextId } from "@jahia/formidable-library";

interface PhoneProps extends TextValidationMessageProps {
  "jcr:title"?: string;
  "helpText"?: string;
  "placeholder"?: string;
  "defaultValue"?: string;
  "pattern"?: string;
  "minLength"?: number;
  "maxLength"?: number;
  "autocomplete"?: string;
  "required"?: boolean;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:phone",
    name: "default",
  },
  (
    {
      "jcr:title": label,
      helpText,
      placeholder,
      defaultValue,
      pattern,
      minLength,
      maxLength,
      autocomplete,
      required,
      ...validationMsgs
    }: PhoneProps,
    { currentNode },
  ) => {
    const inputName = currentNode.getName();
    const nodeId = currentNode.getIdentifier();
    const inputId = `phone-${nodeId}`;
    const helpId = helpText ? helpTextId(nodeId) : undefined;

    return (
      <div className="fmdb-form-group fmdbext-phone">
        {label && (
          <label htmlFor={inputId} className="fmdb-form-label">
            {label}
            {required && (
              <span className="fmdb-required-indicator" aria-hidden="true">
                *
              </span>
            )}
          </label>
        )}
        <HelpText id={helpId} text={helpText} />
        <input
          type="tel"
          id={inputId}
          name={inputName}
          className="fmdb-form-control fmdbext-phone-input"
          aria-describedby={helpId}
          // aria-label fallback: without a title the input has no visible label
          aria-label={label ? undefined : inputName}
          placeholder={placeholder}
          defaultValue={defaultValue}
          // An empty pattern is a cleared one: the attribute would otherwise only accept an empty value
          pattern={pattern || undefined}
          minLength={minLength}
          maxLength={maxLength}
          required={required}
          autoComplete={autocomplete}
          {...validationDataAttributes(validationMsgs)}
        />
      </div>
    );
  },
);
