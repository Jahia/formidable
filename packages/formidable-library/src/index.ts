// The markup contract and the input-mask behaviour of Formidable forms, for
// every module that renders a form field: the two form modules of this monorepo
// through the yarn workspace, modules outside it through the published
// `@jahia/formidable-library` package (#308).
//
// - `HelpText` / `helpTextId`: the help block (`div.fmdb-form-help` with id
//   `help-<nodeId>`, referenced by the control's `aria-describedby`);
// - `validationDataAttributes`: the `data-fmdb-msg-*` attributes the validation
//   client reads its custom messages from;
// - the mask utilities and `useMask`: what an input mask stands for — the HTML
//   `pattern`, the formatted default value, the formatting while typing.
//
// Admission criterion — this package is not a grab bag: only code a field view
// must reproduce byte for byte to stay compatible with Formidable's client
// (markup contracts, attribute names), or a behaviour a view outside the
// monorepo could not otherwise reach (the live mask), belongs here.
// Single-module helpers stay in their module. Every export is public API: a
// change here is a change for the modules built against the published package —
// keep README.md in step.
export {HelpText, helpTextId, type HelpTextProps} from "./HelpText";
export {
	validationDataAttributes,
	type BaseValidationMessageProps,
	type TextValidationMessageProps,
	type RangeValidationMessageProps
} from "./validationProps";
export {
	MASK_TOKENS,
	applyMask,
	extractRawValue,
	maskToPattern,
	maskedCursorPosition,
	type ApplyMaskOptions,
	type MaskTokenConfig
} from "./mask";
export {useMask, type UseMaskOptions} from "./useMask";
