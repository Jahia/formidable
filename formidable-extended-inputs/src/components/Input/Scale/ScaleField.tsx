import { AddResources, buildModuleFileUrl } from "@jahia/javascript-modules-library";
import { validationDataAttributes, type BaseValidationMessageProps } from "~/utils/validationProps";
import HelpText, { helpTextId } from "~/design/HelpText";
import "./scale.css";

export interface ScaleProps extends BaseValidationMessageProps {
  "jcr:title"?: string;
  "helpText"?: string;
  "minValue"?: number;
  "maxValue"?: number;
  "step"?: number;
  "minLabel"?: string;
  "maxLabel"?: string;
  "required"?: boolean;
}

export interface ScaleRenderInput {
  props: ScaleProps;
  inputName: string;
  nodeId: string;
  // Set by the nps view to force the standard NPS presentation
  forced?: {
    min: number;
    max: number;
    step: number;
    defaultMinLabel: string;
    defaultMaxLabel: string;
  };
}

const MAX_ITEMS = 21;

export default function ScaleField({ props, inputName, nodeId, forced }: ScaleRenderInput) {
  const { "jcr:title": label, helpText, minLabel, maxLabel, required, ...rest } = props;
  const { minValue, maxValue, step, ...validationMsgs } = rest;
  const vAttrs = validationDataAttributes(validationMsgs);
  const helpId = helpText ? helpTextId(nodeId) : undefined;

  const min = forced ? forced.min : Number(minValue) || 0;
  const rawMax = forced ? forced.max : Number(maxValue) || 10;
  const inc = forced ? forced.step : Math.max(Number(step) || 1, 1);
  const max = Math.max(rawMax, min + inc);

  const values: number[] = [];
  for (let v = min; v <= max && values.length < MAX_ITEMS; v += inc) {
    values.push(v);
  }

  const startLabel = minLabel || forced?.defaultMinLabel;
  const endLabel = maxLabel || forced?.defaultMaxLabel;

  return (
    <>
      <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
      <fieldset className="fmdb-form-group fmdbext-scale" aria-describedby={helpId}>
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
        <div className="fmdbext-scale-items">
          {values.map((value) => {
            const inputId = `scale-${nodeId}-${value}`;
            return (
              <span key={value} className="fmdbext-scale-item">
                <input
                  type="radio"
                  id={inputId}
                  name={inputName}
                  className="fmdb-form-control fmdbext-scale-input"
                  value={value}
                  required={required}
                  {...vAttrs}
                />
                <label htmlFor={inputId} className="fmdbext-scale-label">
                  {value}
                </label>
              </span>
            );
          })}
        </div>
        {(startLabel || endLabel) && (
          <div className="fmdbext-end-labels">
            <span>{startLabel}</span>
            <span>{endLabel}</span>
          </div>
        )}
      </fieldset>
    </>
  );
}
