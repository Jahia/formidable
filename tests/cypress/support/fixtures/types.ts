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
	type?: 'STRING' | 'BOOLEAN' | 'LONG' | 'DATE' | 'WEAKREFERENCE';
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

/**
 * Date input data based on fmdb:inputDate CND
 * Inherits: required, defaultValue, min, max, step
 */
export type InputDateData = InputWithRange;

/**
 * Datetime-local input data based on fmdb:inputDatetimeLocal CND
 * Inherits: required, defaultValue, min, max, step
 */
export type InputDatetimeLocalData = InputWithRange;

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
