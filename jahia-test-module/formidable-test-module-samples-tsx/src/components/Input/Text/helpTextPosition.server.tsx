import { jahiaComponent } from "@jahia/javascript-modules-library";

/** The values of fmdbsamplemix:helpTextPosition; "up" is the built-in rendering. */
type HelpTextPosition = "up" | "down" | "both";

interface InputTextHelpTextPositionProps {
  "jcr:title"?: string;
  "helpText"?: string;
  /** From fmdbsamplemix:helpTextPosition, absent until the node is saved with the mixin. */
  "helpTextPosition"?: string;
  "placeholder"?: string;
  "defaultValue"?: string;
  "list"?: string[];
  "minLength"?: number;
  "maxLength"?: number;
  "required"?: boolean;
  "autocomplete"?: string;
  // fmdbmix:advancedInputTextSettings
  "pattern"?: string;
  "readonly"?: boolean;
  "autofocus"?: boolean;
  "disabled"?: boolean;
  "form"?: string;
  "dirname"?: boolean;
  "spellcheck"?: boolean;
  "size"?: number;
  "title"?: string;
  // fmdbmix:textValidationMessages
  "msgValueMissing"?: string;
  "msgTypeMismatch"?: string;
  "msgPatternMismatch"?: string;
  "msgTooShort"?: string;
  "msgTooLong"?: string;
}

const DEFAULT_LIST: string[] = [];

const readPosition = (value: string | undefined): HelpTextPosition =>
  value === "down" || value === "both" ? value : "up";

/**
 * The inline-validation contract of docs/custom-validation.md, written out rather than imported: a
 * third-party module cannot depend on the monorepo's private shared package.
 */
const validationMessageAttributes = (props: InputTextHelpTextPositionProps) => ({
  "data-fmdb-msg-value-missing": props.msgValueMissing || undefined,
  "data-fmdb-msg-type-mismatch": props.msgTypeMismatch || undefined,
  "data-fmdb-msg-pattern-mismatch": props.msgPatternMismatch || undefined,
  "data-fmdb-msg-too-short": props.msgTooShort || undefined,
  "data-fmdb-msg-too-long": props.msgTooLong || undefined,
});

/**
 * A text input whose help text goes where the contributor put it — above the field (the built-in
 * rendering), below it, or in both places — read from the sample mixin
 * fmdbsamplemix:helpTextPosition. Picked per field through the View chooser, like the fieldset
 * samples. The markup keeps the built-in contracts (docs/how-to-extend-views-and-elements-from-
 * third-party-module.md): the field's name and id, the fmdb-* hooks, one help block with the
 * `help-<nodeId>` id the control references, the data-fmdb-msg-* validation messages. The mask of
 * the built-in view is a client island of the built-in module, out of a third-party view's reach: a
 * masked field keeps the built-in view.
 */
jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdb:inputText",
    name: "helpTextPosition",
    displayName: "Input text - Help text position",
  },
  (props: InputTextHelpTextPositionProps, { currentNode }) => {
    const {
      "jcr:title": label,
      helpText,
      helpTextPosition,
      placeholder,
      defaultValue,
      list = DEFAULT_LIST,
      minLength,
      maxLength,
      required,
      autocomplete,
      pattern,
      readonly,
      autofocus,
      disabled,
      form,
      dirname,
      spellcheck = true,
      size,
      title,
    } = props;

    const position = readPosition(helpTextPosition);
    const nodeId = currentNode.getIdentifier();
    const inputId = `input-${nodeId}`;
    const inputName = currentNode.getName();
    const datalistId = list.length > 0 ? `datalist-${nodeId}` : undefined;
    // The one block assistive technology is pointed to, wherever it stands.
    const helpId = helpText ? `help-${nodeId}` : undefined;
    const helpAbove = position !== "down";
    const helpBelow = position !== "up";

    const inputAttributes = {
      "type": "text",
      "id": inputId,
      "name": inputName,
      "aria-describedby": helpId,
      "className": "fmdb-form-control",
      placeholder,
      "list": datalistId,
      minLength,
      maxLength,
      pattern,
      required,
      "autoComplete": autocomplete,
      "readOnly": readonly,
      "autoFocus": autofocus,
      disabled,
      form,
      // Submits the field's text direction as `{name}.dir`, like the built-in view
      "dirName": dirname ? `${inputName}.dir` : undefined,
      "spellCheck": spellcheck,
      size,
      title,
      ...validationMessageAttributes(props),
    };

    return (
      <div className="fmdb-form-group">
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

        {helpText && helpAbove && (
          <div
            id={helpId}
            className="fmdb-form-help"
            dangerouslySetInnerHTML={{ __html: helpText }}
          />
        )}

        <input {...inputAttributes} defaultValue={defaultValue} />

        {/* "both": the block after the field repeats the one the control already describes —
            no id (ids are unique) and out of the accessibility tree, so a screen reader hears the
            help once. "down": this is the help block, with the id. */}
        {helpText && helpBelow && (
          <div
            id={helpAbove ? undefined : helpId}
            className="fmdb-form-help"
            aria-hidden={helpAbove ? "true" : undefined}
            dangerouslySetInnerHTML={{ __html: helpText }}
          />
        )}

        {list.length > 0 && (
          <datalist id={datalistId}>
            {list.map((option) => (
              <option key={option} value={option} />
            ))}
          </datalist>
        )}
      </div>
    );
  },
);
