import type {
    ChoiceValue,
    ConditionalLogicRule,
    EditorContextLike,
    GraphNode,
    LogicOperator,
    LogicSrcNode,
    SelectorProps,
    SourceFieldOption,
    SourceValueKind
} from './ConditionalLogic.types';
import {getSourceDescriptor, operatorNeedsValue} from './sourceDescriptors';
import {getLogicProvider, PROVIDER_OPERATORS, providerConfigKeys} from './providers';

const VALUE_KINDS: SourceValueKind[] = ['choice', 'date', 'number', 'boolean', 'text'];

/**
 * Sentinel a date rule may carry instead of a fixed date: the submission day, resolved by
 * the runtime evaluators (the browser's local day, an agreed day server-side). Stored in
 * the ordinary value/values keys — a date input can never produce this literal.
 */
export const TODAY_SENTINEL = 'today';

/**
 * The contributor's local calendar day as yyyy-MM-dd (never through toISOString,
 * which reads the UTC day and shifts around midnight for non-UTC contributors).
 */
export const localIsoDay = (): string => {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
};

/**
 * Authoring-time coherence of a date 'between' interval, the sentinel resolved
 * against the contributor's own day. Both bounds are included (a one-day rule is
 * the same date twice, so equality is coherent). 'noMatch': a sentinel side
 * empties the interval today — the runtime ignores such a rule until (unless) the
 * days make it matchable again. 'inverted': two fixed dates in the wrong order —
 * the rule can never match, an authoring error.
 */
export const dateBetweenIssue = (
    values: string[] | undefined,
    editDay: string
): 'noMatch' | 'inverted' | null => {
    const [rawFrom, rawTo] = values ?? [];
    if (!rawFrom || !rawTo) {
        return null;
    }

    const from = rawFrom === TODAY_SENTINEL ? editDay : rawFrom;
    const to = rawTo === TODAY_SENTINEL ? editDay : rawTo;
    if (from <= to) {
        return null;
    }

    return rawFrom === TODAY_SENTINEL || rawTo === TODAY_SENTINEL ? 'noMatch' : 'inverted';
};

const EMPTY_FIELD_RULE: ConditionalLogicRule = {
    logicId: '',
    sourceType: 'field',
    sourceNodeId: '',
    sourceFieldName: '',
    sourceFieldType: '',
    operator: 'in',
    values: []
};

export const parseRule = (value?: string): ConditionalLogicRule => {
    if (!value) {
        return {...EMPTY_FIELD_RULE};
    }

    try {
        const parsed = JSON.parse(value) as Partial<ConditionalLogicRule>;

        return {
            logicId: parsed.logicId ?? '',
            // Kept verbatim even when it names no known provider: normalizing an unknown
            // sourceType to 'field' would present the rule as a field rule and rewrite it
            // on save, destroying data authored against a newer module version.
            sourceType: typeof parsed.sourceType === 'string' && parsed.sourceType !== ''
                ? parsed.sourceType
                : 'field',
            sourceNodeId: parsed.sourceNodeId ?? '',
            sourceFieldKey: typeof parsed.sourceFieldKey === 'string' ? parsed.sourceFieldKey : undefined,
            sourceFieldName: parsed.sourceFieldName ?? '',
            sourceFieldType: parsed.sourceFieldType ?? '',
            valueKind: VALUE_KINDS.includes(parsed.valueKind as SourceValueKind) ? parsed.valueKind : undefined,
            variable: typeof parsed.variable === 'string' ? parsed.variable : undefined,
            param: typeof parsed.param === 'string' ? parsed.param : undefined,
            cookie: typeof parsed.cookie === 'string' ? parsed.cookie : undefined,
            operator: (parsed.operator as LogicOperator) ?? 'in',
            value: typeof parsed.value === 'string' ? parsed.value : undefined,
            values: Array.isArray(parsed.values) ? parsed.values.filter(value => typeof value === 'string') : []
        };
    } catch {
        return {...EMPTY_FIELD_RULE};
    }
};

export const getOperatorsForSource = (source?: SourceFieldOption): LogicOperator[] => {
    const descriptor = getSourceDescriptor(source?.type, source?.valueKind);
    if (!source || !descriptor) {
        return ['in'];
    }

    return descriptor.getOperators(source);
};

export const sanitizeOperator = (source: SourceFieldOption | undefined, operator: LogicOperator): LogicOperator => {
    const operators = getOperatorsForSource(source);
    return operators.includes(operator) ? operator : operators[0];
};

export const sanitizeProviderOperator = (operator: LogicOperator): LogicOperator =>
    PROVIDER_OPERATORS.includes(operator) ? operator : PROVIDER_OPERATORS[0];

/**
 * Serializes a provider rule to its stored shape: the provider's own config key and
 * nothing else. Field-rule keys and the other providers' keys are dropped, so switching a
 * rule's source type never leaves stale metadata behind.
 */
export const normalizeStoredProviderRule = (rule: ConditionalLogicRule): ConditionalLogicRule => {
    const provider = getLogicProvider(rule.sourceType);
    if (!provider) {
        return parseRule(undefined);
    }

    const operator = sanitizeProviderOperator(rule.operator);
    const normalized: ConditionalLogicRule = {
        logicId: rule.logicId,
        sourceType: provider.id,
        [provider.configKey]: (rule[provider.configKey] ?? '').trim(),
        operator
    };

    if (operatorNeedsValue(operator)) {
        normalized.value = rule.value ?? '';
    }

    return normalized;
};

/** Clears every provider config key, used when a rule's source type changes. */
export const clearedProviderConfig = (): Partial<ConditionalLogicRule> =>
    Object.fromEntries(providerConfigKeys().map(key => [key, undefined]));

// Kinds whose operators compare against contributor-typed scalar value(s)
// (a single input, or two for 'between') instead of a choice list.
export const isScalarValueKind = (valueKind?: SourceValueKind): boolean =>
    valueKind === 'date' || valueKind === 'number' || valueKind === 'text';

export const normalizeStoredRule = (
    rule: ConditionalLogicRule,
    source: SourceFieldOption | undefined
): ConditionalLogicRule => {
    const descriptor = getSourceDescriptor(source?.type, source?.valueKind);
    if (!source || !descriptor) {
        return parseRule(undefined);
    }

    const operator = sanitizeOperator(source, rule.operator);
    const base: ConditionalLogicRule = {
        logicId: rule.logicId,
        sourceNodeId: source.id,
        // JSON.stringify drops it when the source has no fieldKey yet; the Java
        // sync backfills it from the resolved source on save.
        sourceFieldKey: source.fieldKey,
        sourceFieldName: source.name,
        sourceFieldType: source.type,
        valueKind: descriptor.valueKind,
        operator
    };

    if (!operatorNeedsValue(operator)) {
        return base;
    }

    // The today sentinel only means "submission day" for the date kind: like the
    // operator sanitation above, re-pointing the rule to a source of another kind
    // must not carry it over as a literal both evaluators would silently fail on.
    const carriedValue = (value: string): string =>
        descriptor.valueKind !== 'date' && rule.valueKind !== descriptor.valueKind && value === TODAY_SENTINEL
            ? ''
            : value;

    if (isScalarValueKind(descriptor.valueKind)) {
        if (operator === 'between') {
            return {...base, values: (rule.values ?? []).slice(0, 2).map(carriedValue)};
        }

        return {...base, value: carriedValue(rule.value ?? '')};
    }

    return {...base, values: rule.values ?? []};
};

export const extractEditorContext = (props: SelectorProps): EditorContextLike | undefined => {
    return props.editorContext ?? props.context;
};

export const extractCurrentNodePath = (props: SelectorProps): string | undefined => {
    const editorContext = extractEditorContext(props);

    return props.field.node?.path
        ?? props.field.nodePath
        ?? props.field.path
        ?? editorContext?.nodeData?.path
        ?? editorContext?.path
        ?? undefined;
};

/**
 * The language of the CONTENT being edited, or undefined when the editor context does
 * not state it.
 *
 * Distinct from extractLanguage, which falls back to the UI language and finally to
 * 'en' so a caller always has something to read. A decision that gates what a
 * contributor may edit has to know when the answer would be a guess: mistaking the
 * default language for another one leaves nobody able to author at all.
 */
export const extractContentLanguage = (props: SelectorProps): string | undefined => {
    const editorContext = extractEditorContext(props);

    return editorContext?.nodeData?.language
        ?? editorContext?.nodeData?.lang
        ?? editorContext?.language
        ?? editorContext?.lang
        ?? undefined;
};

export const extractLanguage = (props: SelectorProps): string => {
    const editorContext = extractEditorContext(props);
    const fromWindow = (window as unknown as {contextJsParameters?: {uilang?: string}}).contextJsParameters?.uilang;

    return editorContext?.nodeData?.language
        ?? editorContext?.nodeData?.lang
        ?? editorContext?.language
        ?? editorContext?.lang
        ?? editorContext?.uilang
        ?? editorContext?.locale
        ?? fromWindow
        ?? 'en';
};

export const extractWorkspace = (props: SelectorProps): string => {
    const editorContext = extractEditorContext(props);
    return editorContext?.nodeData?.workspace ?? editorContext?.workspace ?? 'EDIT';
};

export const findFormPath = (node?: GraphNode | null): string | undefined => {
    const formAncestor = node?.ancestors?.find(a => a.primaryNodeType?.name === 'fmdb:form');
    return formAncestor?.path;
};

const getNodeType = (node?: GraphNode | null): string | undefined => {
    return node?.primaryNodeType?.name ?? undefined;
};

const parseJsonArrayValue = (rawValues: string[] = []): ChoiceValue[] => {
    return rawValues.flatMap(rawValue => {
        try {
            const parsed = JSON.parse(rawValue) as {value?: string; label?: string};
            if (typeof parsed?.value === 'string' && parsed.value !== '') {
                return [{value: parsed.value, label: parsed.label ?? parsed.value}];
            }

            return [];
        } catch {
            return [];
        }
    });
};

// The declared value kind of a field node, from its semantic mixin. A well-formed
// type carries at most one; the order below just makes conflicts deterministic.
const getDeclaredValueKind = (node: GraphNode): SourceValueKind | undefined => {
    if (node.isChoiceField) return 'choice';
    if (node.isDateField) return 'date';
    if (node.isNumberField) return 'number';
    if (node.isBooleanField) return 'boolean';
    if (node.isTextField) return 'text';
    return undefined;
};

/**
 * The choice values a rule can be authored against. The IDENTITY is the site default
 * language's list — the values every language renders and submits in the 0.4 model —
 * labelled in cascade: the current language's label for that value when it has one,
 * else the default language's, else the value itself. The rule stores only the value,
 * so the label is display comfort, and the default language always knows the value:
 * an untranslated (or blank-labelled, freshly realigned) language never shows an
 * unlabeled or misleading list, and never authors a value the identity does not know.
 * Falls back to the current language's own list when the default one is empty.
 */
export const mergeChoiceValues = (
    ownValues: ChoiceValue[],
    defaultValues: ChoiceValue[]
): ChoiceValue[] => {
    if (defaultValues.length === 0) {
        return ownValues;
    }

    const ownLabelByValue = new Map<string, string>();
    for (const choice of ownValues) {
        if (choice.label.trim() !== '') {
            ownLabelByValue.set(choice.value, choice.label);
        }
    }

    return defaultValues.map(choice => ({
        value: choice.value,
        label: ownLabelByValue.get(choice.value)
            ?? (choice.label.trim() === '' ? choice.value : choice.label)
    }));
};

const mapSourceField = (node: GraphNode): SourceFieldOption | null => {
    const type = getNodeType(node);
    const valueKind = getDeclaredValueKind(node);
    const descriptor = getSourceDescriptor(type, valueKind);
    if (!type || !descriptor) {
        return null;
    }

    const choiceProperty = node.properties?.find(property => property.name === descriptor.choiceProperty);
    const defaultChoiceProperty = node.defaultProperties?.find(property => property.name === descriptor.choiceProperty);
    const choiceValues = descriptor.valueKind === 'choice'
        ? mergeChoiceValues(
            parseJsonArrayValue(choiceProperty?.values ?? []),
            parseJsonArrayValue(defaultChoiceProperty?.values ?? []))
        : [];
    const fieldKey = node.properties?.find(property => property.name === 'fieldKey')?.value ?? undefined;

    return {
        id: node.uuid,
        fieldKey,
        name: node.name,
        path: node.path,
        label: node.displayName ?? node.name,
        type,
        valueKind: descriptor.valueKind,
        choiceValues
    };
};

export const buildSourceFieldOptions = (currentNodePath: string, nodes: GraphNode[] = []): SourceFieldOption[] => {
    const currentIndex = nodes.findIndex(node => node.path === currentNodePath);
    if (currentIndex === -1) {
        return [];
    }

    const options = nodes
        .slice(0, currentIndex)
        .map(mapSourceField)
        .filter((node): node is SourceFieldOption => node !== null);

    const labelCounts = new Map<string, number>();
    for (const option of options) {
        labelCounts.set(option.label, (labelCounts.get(option.label) ?? 0) + 1);
    }

    const labelCounters = new Map<string, number>();
    for (const option of options) {
        if ((labelCounts.get(option.label) ?? 0) > 1) {
            const counter = (labelCounters.get(option.label) ?? 0) + 1;
            labelCounters.set(option.label, counter);
            option.label = `${option.label}:${counter}`;
        }
    }

    return options;
};

export const buildLogicIdToSourceMap = (logicSrcNodes: LogicSrcNode[] = []): Map<string, {name: string; uuid: string}> => {
    const map = new Map<string, {name: string; uuid: string}>();
    for (const node of logicSrcNodes) {
        const refNode = node.property?.refNode;
        if (refNode) {
            map.set(node.name, {name: refNode.name, uuid: refNode.uuid});
        }
    }

    return map;
};

/**
 * The value options a rule's dropdown offers, extended with any STORED value absent
 * from the current language's list. Rules are shared across languages while a
 * 0.3-migrated field may keep divergent per-language values until its first save
 * re-aligns them: without the fallback the chip of such a value renders empty, which
 * reads as data loss. The raw value is shown instead — it is what the rule compares.
 */
export const withStoredValues = (
    options: Array<{label: string; value: string}>,
    storedValues: string[]
): Array<{label: string; value: string}> => {
    // An EMPTY label is the other face of the same migration state: the language sync
    // re-aligns a divergent list on the default language's values and blanks the labels
    // that need re-translating — a chip or dropdown row must then say the value, never
    // render blank.
    const labelled = options.map(option =>
        option.label.trim() === '' ? {...option, label: option.value} : option);
    const known = new Set(labelled.map(option => option.value));
    const missing = storedValues
        .filter(value => value !== '' && !known.has(value))
        .map(value => ({label: value, value}));
    return missing.length === 0 ? labelled : [...labelled, ...missing];
};
