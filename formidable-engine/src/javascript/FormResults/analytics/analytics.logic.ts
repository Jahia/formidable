/**
 * Whether a field was shown to the visitor of one submission, read back from the form's conditional logic and
 * the submitted values. The analytics need it to tell a field the visitor left empty from a field the visitor
 * never saw: only the first is a field "left empty".
 *
 * The rules are judged as the engine judges them at submission (ConditionalLogicEvaluator.java): the rules of
 * one element all have to hold, an element inside a hidden container is hidden, and a rule on a hidden source
 * fails. What the submission does not record cannot be judged here: a cookie, a JavaScript variable, a profile
 * property, a date compared with "today". Such a rule answers `unknown`, never a guess. A rule on a URL
 * parameter is read from the page the submission came from (its Referer).
 */

export type Shown = 'shown' | 'hidden' | 'unknown';

export interface LogicRule {
    sourceType: string;
    sourceFieldKey: string;
    sourceFieldName: string;
    valueKind: string;
    operator: string;
    value: string | null;
    values: string[];
    /** A rule on something else than a field: the one entry that names what it reads (`param`, `cookie`...). */
    providerRef: string | null;
}

export interface LogicElement {
    name: string;
    path: string;
    fieldKey: string;
    rules: LogicRule[];
}

export interface LogicSubmission {
    values: Map<string, string[]>;
    referer: string | null;
}

/** The entries every rule may carry; any other string entry of a provider rule is its reference. */
const SHARED_RULE_KEYS = new Set([
    'logicId', 'sourceType', 'sourceNodeId', 'sourceFieldKey', 'sourceFieldName', 'sourceFieldType', 'valueKind', 'operator', 'value', 'values'
]);

const isFieldSource = (sourceType: string): boolean => sourceType === '' || sourceType === 'field';

export function parseLogicRule(json: unknown): LogicRule | null {
    let parsed: unknown;
    try {
        parsed = JSON.parse(String(json));
    } catch {
        return null;
    }

    if (!parsed || typeof parsed !== 'object') {
        return null;
    }

    const raw = parsed as Record<string, unknown>;
    const text = (key: string): string => {
        const value = raw[key];
        return typeof value === 'string' ? value : '';
    };
    const sourceType = text('sourceType').trim();
    if (!text('operator') || (isFieldSource(sourceType) && !text('sourceFieldName'))) {
        return null;
    }

    const refs = Object.keys(raw).filter(key => !SHARED_RULE_KEYS.has(key) && text(key).trim() !== '');
    return {
        sourceType,
        sourceFieldKey: text('sourceFieldKey'),
        sourceFieldName: text('sourceFieldName'),
        valueKind: text('valueKind'),
        operator: text('operator'),
        value: typeof raw.value === 'string' ? raw.value : null,
        values: Array.isArray(raw.values) ? raw.values.map(String) : [],
        // Two candidates is an ambiguity the engine does not interpret either.
        providerRef: !isFieldSource(sourceType) && refs.length === 1 ? text(refs[0]).trim() : null
    };
}

const isTruthy = (value: string): boolean => value.trim() !== '' && value.trim().toLowerCase() !== 'false';

const compareNumbers = (left: string, right: string): number | null => {
    const a = Number.parseFloat(left);
    const b = Number.parseFloat(right);
    return Number.isNaN(a) || Number.isNaN(b) ? null : Math.sign(a - b);
};

function between(rule: LogicRule, values: string[]): boolean {
    const [from, to] = rule.values;
    if (values.length === 0 || !from || !to) {
        return false;
    }

    if (rule.valueKind === 'number') {
        const low = compareNumbers(values[0], from);
        const high = compareNumbers(values[0], to);
        return low !== null && high !== null && low >= 0 && high <= 0;
    }

    return values[0] >= from && values[0] <= to;
}

/** One operator on the submitted values of the source field; null for an operator this page does not know. */
function fieldOperator(rule: LogicRule, values: string[]): boolean | null {
    const first = values[0];
    const expected = rule.value;
    const numbers = (test: (comparison: number) => boolean): boolean => {
        const comparison = first === undefined || expected === null ? null : compareNumbers(first, expected);
        return comparison !== null && test(comparison);
    };

    switch (rule.operator) {
        case 'in':
        case 'containsAny':
            return rule.values.some(value => values.includes(value));
        case 'notIn':
            return values.length > 0 && !rule.values.some(value => values.includes(value));
        case 'containsAll':
            return rule.values.every(value => values.includes(value));
        case 'isChecked':
        case 'isNotEmpty':
            return values.some(value => value.trim() !== '');
        case 'isUnchecked':
        case 'isEmpty':
            return values.every(value => value.trim() === '');
        case 'isTrue':
            return values.some(isTruthy);
        case 'isFalse':
            return !values.some(isTruthy);
        case 'before':
            return first !== undefined && expected !== null && first < expected;
        case 'after':
            return first !== undefined && expected !== null && first > expected;
        case 'on':
            return first !== undefined && expected !== null && first === expected;
        case 'between':
            return between(rule, values);
        case 'eq':
            return numbers(comparison => comparison === 0);
        case 'neq':
            return numbers(comparison => comparison !== 0);
        case 'lt':
            return numbers(comparison => comparison < 0);
        case 'lte':
            return numbers(comparison => comparison <= 0);
        case 'gt':
            return numbers(comparison => comparison > 0);
        case 'gte':
            return numbers(comparison => comparison >= 0);
        case 'equals':
            return first !== undefined && Boolean(expected) && first === expected;
        case 'contains':
            return first !== undefined && Boolean(expected) && first.includes(expected as string);
        default:
            return null;
    }
}

/** A rule on a URL parameter, read from the page the form was submitted from; unknown without that page. */
function urlParamRule(rule: LogicRule, referer: string | null): Shown {
    if (!rule.providerRef || !referer) {
        return 'unknown';
    }

    let actual: string | null;
    try {
        actual = new URL(referer, 'http://localhost').searchParams.get(rule.providerRef);
    } catch {
        return 'unknown';
    }

    const expected = rule.value ?? '';
    const verdict = (satisfied: boolean): Shown => (satisfied ? 'shown' : 'hidden');
    switch (rule.operator) {
        case 'equals':
            return verdict(actual !== null && actual === expected);
        case 'notEquals':
            return verdict(actual !== null && actual !== expected);
        case 'contains':
            return verdict(actual !== null && expected !== '' && actual.includes(expected));
        case 'exists':
            return verdict(actual !== null);
        case 'notExists':
            return verdict(actual === null);
        default:
            return 'unknown';
    }
}

export interface FormLogic {
    /** Whether the element, or a container above it, carries a rule. */
    isConditional: (name: string) => boolean;
    /** What one submission tells of every element: build it once per submission, then ask per field. */
    forSubmission: (submission: LogicSubmission) => (name: string) => Shown;
}

export function buildFormLogic(elements: LogicElement[]): FormLogic {
    const byName = new Map(elements.map(element => [element.name, element]));
    const byKey = new Map(elements.filter(element => element.fieldKey).map(element => [element.fieldKey, element]));
    // The container an element sits in: the element whose path is the longest prefix of its own.
    const parentOf = new Map<string, LogicElement | undefined>(elements.map(element => [
        element.name,
        elements.filter(other => element.path.startsWith(`${other.path}/`)).sort((a, b) => b.path.length - a.path.length)[0]
    ]));

    const isConditional = (name: string): boolean => {
        for (let element = byName.get(name); element; element = parentOf.get(element.name)) {
            if (element.rules.length > 0) {
                return true;
            }
        }

        return false;
    };

    const forSubmission = (submission: LogicSubmission) => {
        const known = new Map<string, Shown>();
        const visiting = new Set<string>();

        const judge = (rule: LogicRule): Shown => {
            if (!isFieldSource(rule.sourceType)) {
                return rule.sourceType === 'urlParam' ? urlParamRule(rule, submission.referer) : 'unknown';
            }

            const source = byKey.get(rule.sourceFieldKey) ?? byName.get(rule.sourceFieldName);
            if (!source) {
                return 'unknown';
            }

            const sourceShown = shown(source.name);
            if (sourceShown !== 'shown') {
                return sourceShown;
            }

            if (rule.valueKind === 'date' && (rule.value === 'today' || rule.values.includes('today'))) {
                return 'unknown';
            }

            const satisfied = fieldOperator(rule, submission.values.get(source.name) ?? []);
            if (satisfied === null) {
                return 'unknown';
            }

            return satisfied ? 'shown' : 'hidden';
        };

        const compute = (element: LogicElement): Shown => {
            const parent = parentOf.get(element.name);
            const parentShown = parent ? shown(parent.name) : 'shown';
            if (parentShown !== 'shown') {
                return parentShown;
            }

            // One rule that provably fails hides the element, whatever the others say.
            const verdicts = new Set(element.rules.map(judge));
            if (verdicts.has('hidden')) {
                return 'hidden';
            }

            return verdicts.has('unknown') ? 'unknown' : 'shown';
        };

        function shown(name: string): Shown {
            const element = byName.get(name);
            // An element the form no longer declares has no rule left to judge it by; a cycle is not followed.
            if (!element || visiting.has(name)) {
                return 'shown';
            }

            let verdict = known.get(name);
            if (verdict === undefined) {
                visiting.add(name);
                verdict = compute(element);
                visiting.delete(name);
                known.set(name, verdict);
            }

            return verdict;
        }

        return shown;
    };

    return {isConditional, forSubmission};
}
