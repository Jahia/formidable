import type {LogicOperator, SourceFieldOption} from './ConditionalLogic.types';

/**
 * Shape of the values a source field produces, which drives the value widget
 * shown next to the operator:
 *  - 'choice': values come from the field's configured choice list (dropdown widget)
 *  - 'date': values are dates (native date input widget)
 */
export type SourceValueKind = 'choice' | 'date';

/**
 * Everything the rules editor needs to know about a field type to offer it as a
 * conditional logic source. All per-type knowledge lives here: eligibility is
 * "a descriptor exists for the type", operators and choice storage are read from
 * the descriptor — nothing else in the editor branches on concrete type names.
 */
export interface LogicSourceDescriptor {
    valueKind: SourceValueKind;
    // JCR property holding the JSON-encoded choice values, for 'choice' sources.
    choiceProperty?: string;
    getOperators: (source: SourceFieldOption) => LogicOperator[];
}

const DESCRIPTORS = new Map<string, LogicSourceDescriptor>([
    ['fmdb:select', {
        valueKind: 'choice',
        choiceProperty: 'options',
        getOperators: () => ['in', 'notIn']
    }],
    ['fmdb:radio', {
        valueKind: 'choice',
        choiceProperty: 'choices',
        getOperators: () => ['in', 'notIn']
    }],
    ['fmdb:checkbox', {
        valueKind: 'choice',
        choiceProperty: 'choices',
        getOperators: source => source.choiceValues.length <= 1
            ? ['isChecked', 'isUnchecked']
            : ['containsAny', 'containsAll']
    }],
    ['fmdb:inputDate', {
        valueKind: 'date',
        getOperators: () => ['before', 'after', 'on', 'between']
    }]
]);

export const getSourceDescriptor = (type?: string): LogicSourceDescriptor | undefined =>
    type ? DESCRIPTORS.get(type) : undefined;

/**
 * Operators that compare against contributor-provided value(s); the others
 * (checked/defined states) need no value widget at all.
 */
export const operatorNeedsValue = (operator: LogicOperator): boolean =>
    !['isChecked', 'isUnchecked', 'exists', 'notExists'].includes(operator);

// Operators available on jsVariable rules (dotted window variable paths).
export const JS_VARIABLE_OPERATORS: LogicOperator[] = [
    'equals',
    'notEquals',
    'contains',
    'exists',
    'notExists'
];
