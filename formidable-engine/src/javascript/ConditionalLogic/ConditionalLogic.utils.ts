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
            sourceType: getLogicProvider(parsed.sourceType)?.id ?? 'field',
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

    if (isScalarValueKind(descriptor.valueKind)) {
        if (operator === 'between') {
            return {...base, values: (rule.values ?? []).slice(0, 2)};
        }

        return {...base, value: rule.value ?? ''};
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

const mapSourceField = (node: GraphNode): SourceFieldOption | null => {
    const type = getNodeType(node);
    const valueKind = getDeclaredValueKind(node);
    const descriptor = getSourceDescriptor(type, valueKind);
    if (!type || !descriptor) {
        return null;
    }

    const choiceProperty = node.properties?.find(property => property.name === descriptor.choiceProperty);
    const choiceValues = descriptor.valueKind === 'choice' ? parseJsonArrayValue(choiceProperty?.values ?? []) : [];
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
