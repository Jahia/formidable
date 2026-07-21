import type { JCRNodeWrapper } from "org.jahia.services.content";

/**
 * The server-side registry, injected as a global into JS module server bundles by the js-modules
 * engine. We only use `registry.add` here; see @jahia/javascript-modules-library for the full
 * surface.
 */
declare const server: {
  registry: { add: (type: string, key: string, entry: Record<string, unknown>) => void };
};

/** Set to the active bundle's symbolic name while server files are evaluated at startup. */
declare const bundleKey: string;

/**
 * The registry type consumed by formidable-engine's JsFieldValidator (keep in sync with the Java
 * side).
 */
const REGISTRY_TYPE = "formidable-field-validator";

/** A single validation failure for a submitted field value. */
export interface FormFieldViolation {
  /** Message surfaced in the submission error. Literal text, or a `{resource.bundle.key}` reference. */
  message: string;
}

/** Declaration of a form-field validator. */
export interface FormFieldValidatorProps {
  /** Field node type (primary or mixin) the validator applies to, matched with `isNodeType()`. */
  nodeType: string;
  /** Distinguishes several validators for the same node type in one module. @default "default" */
  name?: string;
}

/**
 * Registers a server-side validator for a Formidable field type. Formidable runs it during form
 * submission, on the server, for every submitted value of a field of `nodeType` — so a client that
 * bypasses the browser's constraints cannot inject out-of-range values.
 *
 * The callback receives the field's JCR node (to read its configuration, e.g. `maxValue`), the raw
 * submitted `value`, and the submitting session's locale tag. Return a violation to reject the
 * submission, or nothing when the value is acceptable. Empty values should generally be accepted
 * here — presence is enforced separately by the field's `required` constraint.
 *
 * ```ts
 * registerFormFieldValidator({ nodeType: "fmdbext:rating" }, (node, value) => {
 *   const n = Number(value);
 *   if (!Number.isInteger(n) || n < 1) return { message: "Invalid rating" };
 * });
 * ```
 *
 * Requires the js-modules engine SDK ({@link JSServerExtensionInvoker}); with an older engine
 * deployed, Formidable skips JS validation and the submission proceeds unvalidated by this hook.
 */
export const registerFormFieldValidator = (
  { nodeType, name = "default" }: FormFieldValidatorProps,
  validate: (
    node: JCRNodeWrapper,
    value: string,
    locale: string | null,
  ) => FormFieldViolation | undefined,
): void => {
  server.registry.add(REGISTRY_TYPE, `${bundleKey}_${REGISTRY_TYPE}_${nodeType}_${name}`, {
    nodeType,
    validate,
  });
  console.debug(`Registered form field validator for ${nodeType} (${name})`);
};
