import { jahiaComponent } from "@jahia/javascript-modules-library";

/** The values of fmdbsamplemix:helpTextPosition; "up" is the built-in rendering. */
type HelpTextPosition = "up" | "down" | "both";

interface InputTextProps {
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
  "mask"?: string;
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
 * The input-mask tokens of the built-in text input, written out for the same reason as the
 * validation attributes below: `9` digit, `A`/`a` letter (upper/lower case), `X`/`x` alphanumeric
 * (upper/lower case), anything else a fixed literal. Two of the three things the built-in view
 * derives from a mask are reproduced here — the HTML `pattern` the browser and the server validate
 * against, and the formatting of a prefilled default; the third, formatting while typing, is a
 * client island of Formidable a third-party view cannot reach.
 */
const MASK_TOKENS: Record<
  string,
  { test: RegExp; source: string; transform?: (c: string) => string }
> = {
  "9": { test: /[0-9]/, source: "[0-9]" },
  "A": { test: /[a-zA-Z]/, source: "[A-Za-z]", transform: (c) => c.toUpperCase() },
  "a": { test: /[a-zA-Z]/, source: "[A-Za-z]", transform: (c) => c.toLowerCase() },
  "X": { test: /[a-zA-Z0-9]/, source: "[A-Za-z0-9]", transform: (c) => c.toUpperCase() },
  "x": { test: /[a-zA-Z0-9]/, source: "[A-Za-z0-9]", transform: (c) => c.toLowerCase() },
};

/** The regular expression of the `pattern` attribute a mask stands for. */
const maskToPattern = (mask: string): string =>
  `^${Array.from(mask)
    .map((char) => MASK_TOKENS[char]?.source ?? char.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("")}$`;

/** A value formatted by the mask: literals inserted, rejected characters dropped, excess ignored. */
const applyMask = (value: string, mask: string): string => {
  let masked = "";
  let maskIndex = 0;
  let consumedToken = false;
  for (const char of value.replace(/[^a-zA-Z0-9]/g, "")) {
    while (maskIndex < mask.length && !MASK_TOKENS[mask[maskIndex]]) {
      masked += mask[maskIndex++];
    }
    if (maskIndex >= mask.length) break;
    const token = MASK_TOKENS[mask[maskIndex]];
    if (token.test.test(char)) {
      masked += token.transform ? token.transform(char) : char;
      maskIndex++;
      consumedToken = true;
    }
  }
  // Trailing literals are completed once every remaining position is one, so the pattern holds.
  const remainder = mask.slice(maskIndex);
  if (consumedToken && remainder && Array.from(remainder).every((char) => !MASK_TOKENS[char])) {
    masked += remainder;
  }
  return masked;
};

/**
 * The inline-validation contract of docs/custom-validation.md, written out rather than imported: a
 * third-party module cannot depend on the monorepo's private shared package.
 */
const validationMessageAttributes = (props: InputTextProps) => ({
  "data-fmdb-msg-value-missing": props.msgValueMissing || undefined,
  "data-fmdb-msg-type-mismatch": props.msgTypeMismatch || undefined,
  "data-fmdb-msg-pattern-mismatch": props.msgPatternMismatch || undefined,
  "data-fmdb-msg-too-short": props.msgTooShort || undefined,
  "data-fmdb-msg-too-long": props.msgTooLong || undefined,
});

/**
 * TAKES THE PLACE of Formidable's default view of the text input. Formidable registers its
 * `default` view of fmdb:inputText at the default priority (0); this one is registered under the
 * same name with a higher priority, so on every site where this module is enabled, every text input
 * renders through this file — nothing to pick in the View chooser, and the built-in view is no
 * longer reachable there.
 *
 * What it adds: the help text goes where the contributor put it (the sample mixin
 * fmdbsamplemix:helpTextPosition) — above the field, as Formidable renders it, below it, or in both
 * places. Taking a default view over means owning the whole built-in contract
 * (docs/how-to-extend-views-and-elements-from-third-party-module.md): the field's name and id, the
 * fmdb-* hooks, one help block with the `help-<nodeId>` id the control references, the
 * data-fmdb-msg-* validation messages, the `pattern` and formatted default a mask stands for — all
 * kept here. One thing is out of a third-party view's reach: the formatting while typing of a
 * masked field is a client island of Formidable, so on a site enabled for this module a masked text
 * input keeps its format validation but loses its live mask.
 */
jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdb:inputText",
    name: "default",
    // Above Formidable's own default view (priority 0): the highest priority wins.
    priority: 1,
  },
  (props: InputTextProps, { currentNode }) => {
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
      mask,
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
      // An explicit pattern wins; a mask alone stands for one the browser checks without JavaScript
      "pattern": pattern || (mask ? maskToPattern(mask) : undefined),
      "data-mask": mask,
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
      // The data attribute tells this rendering from Formidable's, whatever the position.
      <div className="fmdb-form-group" data-fmdbsample-help-position={position}>
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

        <input
          {...inputAttributes}
          defaultValue={defaultValue && mask ? applyMask(defaultValue, mask) : defaultValue}
        />

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
