// Code shared between the Formidable form modules of this monorepo (issue
// #170): the help-text component (id scheme `help-<nodeId>`, `fmdb-form-help`
// class read by the validation client and theme CSS) and the `data-fmdb-msg-*`
// validation-message attribute emitter.
//
// Admission criterion — this package is not a grab bag: only code that at
// least two modules must keep byte-compatible belongs here (markup contracts,
// shared attribute names). Single-module helpers stay in their module.
//
// This package is private and consumed from source through the yarn workspace:
// each module's vite build bundles it. Genuinely third-party modules (outside
// this monorepo) cannot depend on it — for them the contract is documented in
// docs/how-to-extend-views-and-elements-from-third-party-module.md and
// docs/custom-validation.md.
export {HelpText, helpTextId, type HelpTextProps} from './HelpText';
export {
	validationDataAttributes,
	type BaseValidationMessageProps,
	type TextValidationMessageProps,
	type RangeValidationMessageProps
} from './validationProps';
