import { jahiaComponent } from "@jahia/javascript-modules-library";
import { type TextValidationMessageProps, validationDataAttributes } from "@jahia/formidable-library";
import { HelpText, helpTextId } from "@jahia/formidable-library";

interface PasswordProps extends TextValidationMessageProps {
  "jcr:title"?: string;
  "helpText"?: string;
  "placeholder"?: string;
  "required"?: boolean;
  "autocompleteMode"?: string;
  "minLength"?: number;
  "maxLength"?: number;
}

/** The two tokens the browser's password manager understands; anything else (a stale value) reads as a new password. */
const AUTOCOMPLETE_TOKENS = new Set(["new-password", "current-password"]);

/** The HTML `maxlength` when the node has none: defensive only, the CND makes the property mandatory with this default. */
const DEFAULT_MAX_LENGTH = 128;

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:password",
    name: "default",
  },
  (
    {
      "jcr:title": label,
      helpText,
      placeholder,
      required = true,
      autocompleteMode,
      minLength,
      maxLength = DEFAULT_MAX_LENGTH,
      ...validationMsgs
    }: PasswordProps,
    { currentNode },
  ) => {
    const inputName = currentNode.getName();
    const nodeId = currentNode.getIdentifier();
    const inputId = `password-${nodeId}`;
    const helpId = helpText ? helpTextId(nodeId) : undefined;
    const autocomplete =
      autocompleteMode && AUTOCOMPLETE_TOKENS.has(autocompleteMode) ? autocompleteMode : "new-password";

    return (
      <div className="fmdb-form-group fmdbext-password">
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
        {/* Never a default value: the markup of a password field must hold no secret, and a cached view is shared */}
        <input
          type="password"
          id={inputId}
          name={inputName}
          className="fmdb-form-control fmdbext-password-input"
          aria-describedby={helpId}
          // aria-label fallback: without a title the input has no visible label
          aria-label={label ? undefined : inputName}
          placeholder={placeholder}
          required={required}
          minLength={minLength}
          maxLength={maxLength}
          autoComplete={autocomplete}
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          {...validationDataAttributes(validationMsgs)}
        />
      </div>
    );
  },
);
