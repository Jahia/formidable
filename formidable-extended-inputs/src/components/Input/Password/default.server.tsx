import { jahiaComponent } from "@jahia/javascript-modules-library";
import {
  HelpText,
  helpTextId,
  type TextValidationMessageProps,
  validationDataAttributes,
} from "@jahia/formidable-library";

interface PasswordProps extends TextValidationMessageProps {
  "jcr:title"?: string;
  helpText?: string;
  placeholder?: string;
  required?: boolean;
  autocompleteMode?: string;
  minLength?: number;
  maxLength?: number;
}

/** The two tokens the browser's password manager understands; anything else (a stale value) reads as a new password. */
const AUTOCOMPLETE_TOKENS = new Set(["new-password", "current-password"]);

/** The HTML `maxlength` when the author set none: the CND defaults to it, a field saved before the default has none. */
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
    const nodeId = currentNode.getIdentifier();
    const inputId = `input-${nodeId}`;
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
          name={currentNode.getName()}
          className="fmdb-form-control"
          aria-describedby={helpId}
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
