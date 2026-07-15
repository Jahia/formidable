export type SupportedConditionalSourceType = 'fmdb:select' | 'fmdb:radio' | 'fmdb:checkbox' | 'fmdb:inputDate';

export type ConditionalLogicSourceType = 'field' | 'datalayer';

export type ConditionalLogicOperator =
	| 'in'
	| 'notIn'
	| 'isChecked'
	| 'isUnchecked'
	| 'containsAny'
	| 'containsAll'
	| 'before'
	| 'after'
	| 'on'
	| 'between'
	| 'equals'
	| 'notEquals'
	| 'contains'
	| 'exists'
	| 'notExists';

export interface ConditionalLogicRule {
	logicId?: string;
	// Absent on rules stored before datalayer support; treated as 'field'.
	sourceType?: ConditionalLogicSourceType;
	sourceFieldName?: string;
	sourceNodeId?: string;
	sourceFieldType?: SupportedConditionalSourceType;
	datalayerVariable?: string;
	operator: ConditionalLogicOperator;
	value?: string;
	values?: string[];
}

const SUPPORTED_TYPES: SupportedConditionalSourceType[] = [
	'fmdb:select',
	'fmdb:radio',
	'fmdb:checkbox',
	'fmdb:inputDate'
];

const isDatalayerRuleShape = (parsed: Partial<ConditionalLogicRule>): boolean =>
	parsed.sourceType === 'datalayer'
	&& typeof parsed.datalayerVariable === 'string'
	&& parsed.datalayerVariable.trim() !== '';

export const parseConditionalLogicRule = (rawValue: string): ConditionalLogicRule | null => {
	try {
		const parsed = JSON.parse(rawValue) as Partial<ConditionalLogicRule>;
		if (!parsed || typeof parsed.operator !== 'string') {
			return null;
		}

		if (isDatalayerRuleShape(parsed)) {
			return {
				logicId: typeof parsed.logicId === 'string' ? parsed.logicId : undefined,
				sourceType: 'datalayer',
				datalayerVariable: parsed.datalayerVariable!.trim(),
				operator: parsed.operator as ConditionalLogicOperator,
				value: typeof parsed.value === 'string' ? parsed.value : undefined
			};
		}

		if (typeof parsed.sourceFieldName !== 'string') {
			return null;
		}

		if (!SUPPORTED_TYPES.includes(parsed.sourceFieldType as SupportedConditionalSourceType)) {
			return null;
		}

		return {
			logicId: typeof parsed.logicId === 'string' ? parsed.logicId : undefined,
			sourceFieldName: parsed.sourceFieldName,
			sourceNodeId: typeof parsed.sourceNodeId === 'string' ? parsed.sourceNodeId : undefined,
			sourceFieldType: parsed.sourceFieldType as SupportedConditionalSourceType,
			operator: parsed.operator as ConditionalLogicOperator,
			value: typeof parsed.value === 'string' ? parsed.value : undefined,
			values: Array.isArray(parsed.values) ? parsed.values.filter(value => typeof value === 'string') : undefined
		};
	} catch {
		return null;
	}
};

export const parseConditionalLogicRules = (rawValues: string[] = []): ConditionalLogicRule[] => {
	return rawValues
		.map(parseConditionalLogicRule)
		.filter((rule): rule is ConditionalLogicRule => rule !== null);
};

const isConditionalLogicRule = (value: unknown): value is ConditionalLogicRule => {
	if (!value || typeof value !== 'object') return false;
	const candidate = value as Partial<ConditionalLogicRule>;
	if (typeof candidate.operator !== 'string') return false;
	if (isDatalayerRuleShape(candidate)) return true;
	return typeof candidate.sourceFieldName === 'string'
		&& SUPPORTED_TYPES.includes(candidate.sourceFieldType as SupportedConditionalSourceType);
};

const deserializeConditionalLogicRules = (rawValue: string): ConditionalLogicRule[] => {
	try {
		const parsed = JSON.parse(rawValue) as unknown;
		if (!Array.isArray(parsed)) return [];

		if (parsed.every(entry => typeof entry === 'string')) {
			return parseConditionalLogicRules(parsed as string[]);
		}

		return parsed.filter(isConditionalLogicRule);
	} catch {
		return [];
	}
};

interface SourceFieldState {
	values: string[];
	checked: boolean;
}

const getSourceFieldState = (wrapper: HTMLElement): SourceFieldState => {
	const select = wrapper.querySelector<HTMLSelectElement>('select');
	if (select) {
		if (select.multiple) {
			return {
				values: Array.from(select.selectedOptions).map(option => option.value).filter(Boolean),
				checked: false
			};
		}

		return {
			values: select.value ? [select.value] : [],
			checked: false
		};
	}

	const radioInputs = Array.from(wrapper.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
	if (radioInputs.length > 0) {
		const selected = radioInputs.find(input => input.checked);
		return {
			values: selected?.value ? [selected.value] : [],
			checked: false
		};
	}

	const checkboxInputs = Array.from(wrapper.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
	if (checkboxInputs.length > 0) {
		if (checkboxInputs.length === 1) {
			return {
				values: checkboxInputs[0].checked && checkboxInputs[0].value ? [checkboxInputs[0].value] : [],
				checked: checkboxInputs[0].checked
			};
		}

		return {
			values: checkboxInputs.filter(input => input.checked).map(input => input.value).filter(Boolean),
			checked: false
		};
	}

	const dateInput = wrapper.querySelector<HTMLInputElement>('input[type="date"]');
	if (dateInput) {
		return {
			values: dateInput.value ? [dateInput.value] : [],
			checked: false
		};
	}

	return {values: [], checked: false};
};

const compareDate = (left: string, right: string): number => {
	if (left === right) return 0;
	return left < right ? -1 : 1;
};

const DATALAYER_PATH_PATTERN = /^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*$/;

/**
 * Safely resolves a dotted variable path (e.g. "window.cxs.profileProperties.firstName")
 * against the window object. Returns undefined when any segment is missing or the
 * path is not a plain dotted identifier chain.
 */
export const resolveDatalayerValue = (variable: string): unknown => {
	if (typeof window === 'undefined') return undefined;
	const path = variable.trim().replace(/^window\./, '');
	if (!DATALAYER_PATH_PATTERN.test(path)) return undefined;

	let current: unknown = window;
	for (const segment of path.split('.')) {
		if (current === null || current === undefined) return undefined;
		current = (current as Record<string, unknown>)[segment];
	}

	return current;
};

const evaluateDatalayerRule = (rule: ConditionalLogicRule): boolean => {
	const raw = resolveDatalayerValue(rule.datalayerVariable ?? '');
	const defined = raw !== undefined && raw !== null;
	const actual = defined ? String(raw) : undefined;
	const expected = rule.value ?? '';

	switch (rule.operator) {
		case 'equals':
			return actual !== undefined && actual === expected;
		case 'notEquals':
			return actual !== undefined && actual !== expected;
		case 'contains':
			return actual !== undefined && expected !== '' && actual.includes(expected);
		case 'exists':
			return defined;
		case 'notExists':
			return !defined;
		default:
			return false;
	}
};

/**
 * Lists the distinct datalayer variables referenced by conditional logic rules
 * inside the form. Used to decide whether a datalayer watcher is needed.
 */
export const collectDatalayerVariables = (form: HTMLFormElement): string[] => {
	const variables = new Set<string>();
	for (const wrapper of Array.from(form.querySelectorAll<HTMLElement>('[data-fmdb-logics]'))) {
		for (const rule of deserializeConditionalLogicRules(wrapper.dataset.fmdbLogics ?? '')) {
			if (rule.sourceType === 'datalayer' && rule.datalayerVariable) {
				variables.add(rule.datalayerVariable);
			}
		}
	}

	return Array.from(variables);
};

/**
 * Builds a comparable snapshot of the current datalayer values so a watcher can
 * detect changes cheaply. Undefined/null are encoded distinctly from their
 * string representations.
 */
export const getDatalayerSnapshot = (variables: string[]): string =>
	JSON.stringify(variables.map(variable => {
		const raw = resolveDatalayerValue(variable);
		if (raw === undefined) return '\u0000undefined';
		if (raw === null) return '\u0000null';
		return String(raw);
	}));

const evaluateRule = (rule: ConditionalLogicRule, sourceWrapper: HTMLElement): boolean => {
	const state = getSourceFieldState(sourceWrapper);
	const values = state.values;
	const expectedValues = rule.values ?? [];

	switch (rule.operator) {
		case 'in':
			return expectedValues.some(value => values.includes(value));
		case 'notIn':
			return values.length > 0 && expectedValues.every(value => !values.includes(value));
		case 'isChecked':
			return state.checked;
		case 'isUnchecked':
			return !state.checked;
		case 'containsAny':
			return expectedValues.some(value => values.includes(value));
		case 'containsAll':
			return expectedValues.every(value => values.includes(value));
		case 'before':
			return values.length > 0 && !!rule.value && compareDate(values[0], rule.value) < 0;
		case 'after':
			return values.length > 0 && !!rule.value && compareDate(values[0], rule.value) > 0;
		case 'on':
			return values.length > 0 && !!rule.value && compareDate(values[0], rule.value) === 0;
		case 'between':
			return values.length > 0
				&& expectedValues.length >= 2
				&& expectedValues[0] !== ''
				&& expectedValues[1] !== ''
				&& compareDate(values[0], expectedValues[0]) >= 0
				&& compareDate(values[0], expectedValues[1]) <= 0;
		default:
			return false;
	}
};

const toggleDescendantControls = (wrapper: HTMLElement, disabled: boolean) => {
	const controls = wrapper.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement | HTMLButtonElement>(
		'input, select, textarea, button'
	);

	for (const control of Array.from(controls)) {
		if (disabled) {
			if (!control.dataset.fmdbInitialDisabled) {
				control.dataset.fmdbInitialDisabled = control.disabled ? 'true' : 'false';
			}

			control.disabled = true;
			continue;
		}

		control.disabled = control.dataset.fmdbInitialDisabled === 'true';
		delete control.dataset.fmdbInitialDisabled;
	}
};

const setWrapperVisibility = (wrapper: HTMLElement, visible: boolean) => {
	wrapper.style.display = visible ? '' : 'none';
	wrapper.setAttribute('aria-hidden', visible ? 'false' : 'true');
	wrapper.dataset.fmdbLogicHidden = visible ? 'false' : 'true';
	toggleDescendantControls(wrapper, !visible);
};

export const applyConditionalLogicVisibility = (form: HTMLFormElement) => {
	const wrappers = Array.from(form.querySelectorAll<HTMLElement>('[data-fmdb-node-name]'));
	const wrappersByNodeId = new Map(
		wrappers
			.filter(w => w.dataset.fmdbNodeId)
			.map(w => [w.dataset.fmdbNodeId!, w])
	);
	const wrappersByName = new Map(
		wrappers
			.filter(w => w.dataset.fmdbNodeName)
			.map(w => [w.dataset.fmdbNodeName!, w])
	);

	for (const wrapper of wrappers) {
		const rawRules = wrapper.dataset.fmdbLogics;
		if (!rawRules) {
			continue;
		}

		const rules = deserializeConditionalLogicRules(rawRules);

		if (rules.length === 0) {
			setWrapperVisibility(wrapper, true);
			continue;
		}

		const visible = rules.every(rule => {
			if (rule.sourceType === 'datalayer') {
				return evaluateDatalayerRule(rule);
			}

			const sourceWrapper = rule.sourceNodeId
				? wrappersByNodeId.get(rule.sourceNodeId)
				: wrappersByName.get(rule.sourceFieldName ?? '');

			if (!sourceWrapper) return false;
			if (sourceWrapper.closest('[data-fmdb-logic-hidden="true"]')) return false;
			return evaluateRule(rule, sourceWrapper);
		});

		setWrapperVisibility(wrapper, visible);
	}
};
