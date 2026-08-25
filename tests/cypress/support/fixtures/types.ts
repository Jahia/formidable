/**
 * Common types for Jahia node creation in Cypress tests
 * Based on CND definitions in /src/components
 */

/**
 * Represents a property of a Jahia node
 */
export interface NodeProperty {
	name: string;
	value?: string;
	values?: string[];
	language?: string;
	type?: 'STRING' | 'BOOLEAN' | 'LONG' | 'DOUBLE' | 'DATE' | 'WEAKREFERENCE';
}

/**
 * Base structure for a Jahia node
 */
export interface JahiaNode {
	name: string;
	primaryNodeType: string;
	properties: NodeProperty[];
	children?: JahiaNode[];
	mixins?: string[];
}

/**
 * Base interface for form element data
 * All form elements extend fmdb:element which extends mix:title
 */
export interface BaseFormElementData {
	name?: string;
	title?: string; // From mix:title (jcr:title)
	helpText?: string; // Rich text (i18n) declared on each field definition
}

/**
 * Base interface for input elements with validation
 * Based on common properties found in CND definitions
 */
export interface BaseInputData extends BaseFormElementData {
	required?: boolean;
}

/**
 * Interface for inputs with defaultValue
 */
export interface InputWithDefaultValue extends BaseInputData {
	defaultValue?: string;
}

/**
 * Interface for inputs with placeholder
 */
export interface InputWithPlaceholder extends BaseInputData {
	placeholder?: string;
}

/**
 * Interface for inputs with min/max/step (date, datetime-local, number, range)
 */
export interface InputWithRange extends InputWithDefaultValue {
	min?: string;
	max?: string;
	step?: number;
}

/**
 * Interface for text-based inputs with length constraints
 */
export interface InputWithLength extends InputWithDefaultValue, InputWithPlaceholder {
	minLength?: number;
	maxLength?: number;
	pattern?: string;
}

/**
 * Button input data based on fmdb:inputButton CND
 */
export interface InputButtonData extends BaseFormElementData {
	buttonType?: 'submit' | 'button' | 'reset';
	variant?: 'primary' | 'secondary' | 'danger';
}

/**
 * Choice item based on SelectOptions JSON structure
 */
export interface ChoiceData {
	value: string;
	label: string;
	selected?: boolean;
}

/**
 * Checkbox data based on fmdb:checkbox CND
 * 1 choice → standalone checkbox, N choices → checkbox group
 */
export interface CheckboxData extends BaseInputData {
	choices: ChoiceData[];
}

/**
 * Radio data based on fmdb:radio CND
 * 1 choice → standalone radio, N choices → radio group
 */
export interface RadioData extends BaseInputData {
	choices: ChoiceData[];
}

/**
 * Select data based on fmdb:select CND
 */
export interface SelectData extends BaseInputData {
	options: ChoiceData[];
	/** Label of the value-less option prepended so the field starts empty (single select only). */
	emptyLabel?: string;
	multiple?: boolean;
	size?: number;
	disabled?: boolean;
	autofocus?: boolean;
}

/**
 * Color input data based on fmdb:inputColor CND
 * Note: Currently only has required and defaultValue
 * Future: alpha, colorspace for advanced settings
 */
export type InputColorData = InputWithDefaultValue;

/** One bound of a date/datetime input: nothing, a fixed value, the submission day, or that day shifted. */
export type DateBoundMode = 'none' | 'date' | 'today' | 'relative';

/** Offset of a 'relative' bound: the submission day shifted by a signed amount. */
export interface RelativeBoundOffset {
	amount: number;
	unit: 'days' | 'months' | 'years';
}

/**
 * Bound modes of fmdb:inputDate / fmdb:inputDatetimeLocal (fmdbmix:dateBounds /
 * fmdbmix:datetimeBounds contracts). The builders imply 'date' when a fixed
 * min/max value is given and 'relative' when an offset is given, so callers
 * only set a mode explicitly for 'today'.
 */
export interface InputWithBoundModes {
	minBoundMode?: DateBoundMode;
	maxBoundMode?: DateBoundMode;
	minRelative?: RelativeBoundOffset;
	maxRelative?: RelativeBoundOffset;
}

/**
 * Date input data based on fmdb:inputDate CND
 * Inherits: required, defaultValue, min, max, step + the bound modes
 */
export type InputDateData = InputWithRange & InputWithBoundModes;

/**
 * Datetime-local input data based on fmdb:inputDatetimeLocal CND
 * Inherits: required, defaultValue, min, max, step + the bound modes
 */
export type InputDatetimeLocalData = InputWithRange & InputWithBoundModes;

/**
 * Email input data based on fmdb:inputEmail CND
 */
export interface InputEmailData extends InputWithLength {
	multiple?: boolean;
	autocomplete?: string;
	list?: string[];
}

export interface InputTextData extends InputWithLength {
	autocomplete?: string;
	list?: string[];
	mask?: string;
}

export interface InputNumberData extends InputWithPlaceholder {
	defaultValue?: number;
	minValue?: number;
	maxValue?: number;
	step?: number;
	list?: string[];
}

export interface InputRangeData extends BaseInputData {
	defaultValue?: number;
	minValue?: number;
	maxValue?: number;
	step?: number;
	minLabel?: string;
	maxLabel?: string;
	list?: string[];
}

export interface InputFileData extends BaseInputData {
	accept?: string[];
	multiple?: boolean;
}

export interface TextareaData extends InputWithLength {
	rows?: number;
	cols?: number;
	autocomplete?: string;
	spellcheck?: boolean;
	readonly?: boolean;
	autofocus?: boolean;
	disabled?: boolean;
	wrap?: 'soft' | 'hard' | 'off';
	resize?: 'none' | 'both' | 'horizontal' | 'vertical';
}

export interface FieldsetData extends BaseFormElementData {
	children?: JahiaNode[];
}

export interface RichTextData {
	name?: string;
	text: string;
}

export interface StepData extends BaseFormElementData {
	label?: string;
	intro?: string;
	children?: JahiaNode[];
}
