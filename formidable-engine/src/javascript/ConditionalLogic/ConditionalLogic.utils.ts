import type {
    ChoiceValue,
    ConditionalLogicRule,
    EditorContextLike,
    GraphNode,
    LogicOperator,
    LogicSrcNode,
    SelectorProps,
    SourceFieldOption,
    SupportedSourceType
} from './ConditionalLogic.types';

export const SUPPORTED_SOURCE_TYPES: SupportedSourceType[] = [
    'fmdb:select',
    'fmdb:radio',
    'fmdb:checkbox',
    'fmdb:inputDate'
];

export const DATALAYER_OPERATORS: LogicOperator[] = [
    'equals',
    'notEquals',
    'contains',
    'exists',
    'notExists'
];

export const datalayerOperatorNeedsValue = (operator: LogicOperator): boolean =>
    operator !== 'exists' && operator !== 'notExists';

export const parseRule = (value?: string): ConditionalLogicRule => {
    if (!value) {
        return {
            logicId: '',
            sourceType: 'field',
            sourceNodeId: '',
            sourceFieldName: '',
            sourceFieldType: 'fmdb:select',
            operator: 'in',
            values: []
        };
    }

    try {
        const parsed = JSON.parse(value) as Partial<ConditionalLogicRule>;
        const sourceFieldType = SUPPORTED_SOURCE_TYPES.includes(parsed.sourceFieldType as SupportedSourceType)
            ? parsed.sourceFieldType as SupportedSourceType
            : 'fmdb:select';

        return {
            logicId: parsed.logicId ?? '',
            sourceType: parsed.sourceType === 'datalayer' ? 'datalayer' : 'field',
            sourceNodeId: parsed.sourceNodeId ?? '',
            sourceFieldName: parsed.sourceFieldName ?? '',
            sourceFieldType,
            datalayerVariable: typeof parsed.datalayerVariable === 'string' ? parsed.datalayerVariable : undefined,
            operator: (parsed.operator as LogicOperator) ?? 'in',
            value: typeof parsed.value === 'string' ? parsed.value : undefined,
            values: Array.isArray(parsed.values) ? parsed.values.filter(value => typeof value === 'string') : []
        };
    } catch {
        return {
            logicId: '',
            sourceType: 'field',
            sourceNodeId: '',
            sourceFieldName: '',
            sourceFieldType: 'fmdb:select',
            operator: 'in',
            values: []
        };
    }
};

export const getOperatorsForSource = (source?: SourceFieldOption): LogicOperator[] => {
    if (!source) {
        return ['in'];
    }

    switch (source.type) {
        case 'fmdb:select':
        case 'fmdb:radio':
            return ['in', 'notIn'];
        case 'fmdb:checkbox':
            return source.choiceValues.length <= 1
                ? ['isChecked', 'isUnchecked']
                : ['containsAny', 'containsAll'];
        case 'fmdb:inputDate':
            return ['before', 'after', 'on', 'between'];
        default:
            return ['in'];
    }
};

export const sanitizeOperator = (source: SourceFieldOption | undefined, operator: LogicOperator): LogicOperator => {
    const operators = getOperatorsForSource(source);
    return operators.includes(operator) ? operator : operators[0];
};

export const sanitizeDatalayerOperator = (operator: LogicOperator): LogicOperator =>
    DATALAYER_OPERATORS.includes(operator) ? operator : DATALAYER_OPERATORS[0];

export const normalizeStoredDatalayerRule = (rule: ConditionalLogicRule): ConditionalLogicRule => {
    const operator = sanitizeDatalayerOperator(rule.operator);
    const normalized: ConditionalLogicRule = {
        logicId: rule.logicId,
        sourceType: 'datalayer',
        sourceNodeId: '',
        sourceFieldName: '',
        sourceFieldType: 'fmdb:select',
        datalayerVariable: (rule.datalayerVariable ?? '').trim(),
        operator
    };

    if (datalayerOperatorNeedsValue(operator)) {
        normalized.value = rule.value ?? '';
    }

    return normalized;
};

export const normalizeStoredRule = (
    rule: ConditionalLogicRule,
    source: SourceFieldOption | undefined
): ConditionalLogicRule => {
    if (!source) {
        return parseRule(undefined);
    }

    const operator = sanitizeOperator(source, rule.operator);

    if (source.type === 'fmdb:inputDate') {
        if (operator === 'between') {
            return {
                logicId: rule.logicId,
                sourceNodeId: source.id,
                sourceFieldName: source.name,
                sourceFieldType: source.type,
                operator,
                values: (rule.values ?? []).slice(0, 2)
            };
        }

        return {
            logicId: rule.logicId,
            sourceNodeId: source.id,
            sourceFieldName: source.name,
            sourceFieldType: source.type,
            operator,
            value: rule.value ?? ''
        };
    }

    if (source.type === 'fmdb:checkbox' && source.choiceValues.length <= 1) {
        return {
            logicId: rule.logicId,
            sourceNodeId: source.id,
            sourceFieldName: source.name,
            sourceFieldType: source.type,
            operator
        };
    }

    return {
        logicId: rule.logicId,
        sourceNodeId: source.id,
        sourceFieldName: source.name,
        sourceFieldType: source.type,
        operator,
        values: rule.values ?? []
    };
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

const mapSourceField = (node: GraphNode): SourceFieldOption | null => {
    const type = getNodeType(node);
    if (!type || !SUPPORTED_SOURCE_TYPES.includes(type as SupportedSourceType)) {
        return null;
    }

    const choicePropertyName = type === 'fmdb:select' ? 'options' : 'choices';
    const choiceProperty = node.properties?.find(property => property.name === choicePropertyName);
    const choiceValues = type === 'fmdb:inputDate' ? [] : parseJsonArrayValue(choiceProperty?.values ?? []);

    return {
        id: node.uuid,
        name: node.name,
        path: node.path,
        label: node.displayName ?? node.name,
        type: type as SupportedSourceType,
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

