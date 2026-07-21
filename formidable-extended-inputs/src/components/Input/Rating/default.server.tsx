import {
  AddResources,
  buildModuleFileUrl,
  jahiaComponent,
} from "@jahia/javascript-modules-library";
import { type BaseValidationMessageProps, validationDataAttributes } from "~/utils/validationProps";
import HelpText, { helpTextId } from "~/design/HelpText";
import "./rating.css";

interface RatingProps extends BaseValidationMessageProps {
  "jcr:title"?: string;
  "helpText"?: string;
  "icon"?: string;
  "maxValue"?: number;
  "minLabel"?: string;
  "maxLabel"?: string;
  "required"?: boolean;
}

const MAX_ITEMS = 10;

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdbext:rating",
    name: "default",
  },
  (
    {
      "jcr:title": label,
      helpText,
      icon = "star",
      maxValue,
      minLabel,
      maxLabel,
      required,
      ...validationMsgs
    }: RatingProps,
    { currentNode },
  ) => {
    const inputName = currentNode.getName();
    const nodeId = currentNode.getIdentifier();
    const vAttrs = validationDataAttributes(validationMsgs);
    const helpId = helpText ? helpTextId(nodeId) : undefined;

    const max = Math.min(Math.max(Number(maxValue) || 5, 2), MAX_ITEMS);
    // DOM order is max..1: combined with row-reverse this lets pure CSS
    // (input:checked ~ label) fill every icon up to the selected one.
    const values = Array.from({ length: max }, (_, i) => max - i);

    return (
      <>
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
        <fieldset className="fmdb-form-group fmdbext-rating" aria-describedby={helpId}>
          {label && (
            <legend className="fmdb-group-legend">
              {label}
              {required && (
                <span className="fmdb-required-indicator" aria-hidden="true">
                  *
                </span>
              )}
            </legend>
          )}
          <HelpText id={helpId} text={helpText} />
          <div className="fmdbext-rating-items" data-fmdbext-icon={icon}>
            {values.map((value) => {
              const inputId = `rating-${nodeId}-${value}`;
              return (
                <span key={value} className="fmdbext-rating-item">
                  <input
                    type="radio"
                    id={inputId}
                    name={inputName}
                    className="fmdb-form-control fmdbext-rating-input"
                    value={value}
                    required={required}
                    aria-label={`${value}/${max}`}
                    {...vAttrs}
                  />
                  <label htmlFor={inputId} className="fmdbext-rating-label" aria-hidden="true">
                    {icon === "number" ? value : null}
                  </label>
                </span>
              );
            })}
          </div>
          {(minLabel || maxLabel) && (
            <div className="fmdbext-end-labels" aria-hidden="true">
              <span>{minLabel}</span>
              <span>{maxLabel}</span>
            </div>
          )}
        </fieldset>
      </>
    );
  },
);
