import {JahiaNode, NodeProperty} from './types';

export interface BaseValidationMessages {
	msgValueMissing?: string;
}

export interface TextValidationMessages extends BaseValidationMessages {
	msgTypeMismatch?: string;
	msgPatternMismatch?: string;
	msgTooShort?: string;
	msgTooLong?: string;
}

export interface RangeValidationMessages extends BaseValidationMessages {
	msgRangeUnderflow?: string;
	msgRangeOverflow?: string;
	msgStepMismatch?: string;
	msgBadInput?: string;
}

export type ValidationMessages =
	| BaseValidationMessages
	| TextValidationMessages
	| RangeValidationMessages;

const toValidationProperties = (messages: ValidationMessages): NodeProperty[] => {
	const entries: Array<[string, string | undefined]> = [
		['msgValueMissing', messages.msgValueMissing],
		['msgTypeMismatch', (messages as TextValidationMessages).msgTypeMismatch],
		['msgPatternMismatch', (messages as TextValidationMessages).msgPatternMismatch],
		['msgTooShort', (messages as TextValidationMessages).msgTooShort],
		['msgTooLong', (messages as TextValidationMessages).msgTooLong],
		['msgRangeUnderflow', (messages as RangeValidationMessages).msgRangeUnderflow],
		['msgRangeOverflow', (messages as RangeValidationMessages).msgRangeOverflow],
		['msgStepMismatch', (messages as RangeValidationMessages).msgStepMismatch],
		['msgBadInput', (messages as RangeValidationMessages).msgBadInput],
	];

	return entries
		.filter(([, value]) => value)
		.map(([name, value]) => ({
			name,
			value,
			language: 'en'
		}));
};

export const withValidationMessages = <T extends JahiaNode>(node: T, messages: ValidationMessages): T => ({
	...node,
	properties: [
		...node.properties,
		...toValidationProperties(messages)
	]
});
