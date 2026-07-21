import type { JCRNodeWrapper } from "org.jahia.services.content";
import { registerFormFieldValidator } from "~/framework/registerFormFieldValidator";

// Server-side validators for the extended field types. These re-enforce, on submission, the same
// constraints the fields present in the browser — so a forged POST cannot store out-of-range values.
// Empty values are accepted here; presence is enforced separately by each field's `required` constraint.

const intProp = (node: JCRNodeWrapper, name: string, fallback: number): number => {
  const raw = node.getPropertyAsString(name);
  const n = raw == null ? NaN : parseInt(raw, 10);
  return Number.isFinite(n) ? n : fallback;
};

const clamp = (n: number, lo: number, hi: number): number => Math.min(Math.max(n, lo), hi);

// Rating: an integer between 1 and the configured maximum (clamped to the 2..10 the renderer allows).
registerFormFieldValidator({ nodeType: "fmdbext:rating" }, (node, value) => {
  if (!value) return;
  const max = clamp(intProp(node, "maxValue", 5), 2, 10);
  const n = Number(value);
  if (!Number.isInteger(n) || n < 1 || n > max) {
    return { message: `Rating must be a whole number between 1 and ${max}.` };
  }
});

// Scale: a value on the configured min..max grid at the configured step.
registerFormFieldValidator({ nodeType: "fmdbext:scale" }, (node, value) => {
  if (!value) return;
  const min = intProp(node, "minValue", 0);
  const max = intProp(node, "maxValue", 10);
  const step = Math.max(intProp(node, "step", 1), 1);
  const lo = Math.min(min, max);
  const hi = Math.max(min, max);
  const n = Number(value);
  if (!Number.isFinite(n) || n < lo || n > hi || (n - min) % step !== 0) {
    return { message: `Scale value must be between ${lo} and ${hi} in steps of ${step}.` };
  }
});

// Switch: strictly the boolean strings the field submits.
registerFormFieldValidator({ nodeType: "fmdbext:switch" }, (_node, value) => {
  if (!value) return; // unchecked toggle submits nothing
  if (value !== "true" && value !== "false") {
    return { message: "Switch value must be true or false." };
  }
});

// Consent: when present it must be exactly "true"; when required, absence is rejected too (defense in
// depth — the required check already covers the empty case).
registerFormFieldValidator({ nodeType: "fmdbext:consent" }, (node, value) => {
  const required = (node.getPropertyAsString("required") ?? "true") === "true";
  if (!value) {
    return required ? { message: "Consent is required." } : undefined;
  }
  if (value !== "true") {
    return { message: "Consent value must be true." };
  }
});
