import {gql} from '@apollo/client';
import {JCR_NODE_IDENTITY} from '../graphql';
import type {
    ChoiceValue,
    ConditionalLogicRule,
    EditorContextLike,
    GraphNode,
    GraphParentNode,
    LogicOperator,
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

export const CURRENT_NODE_BY_PATH = gql`
    ${JCR_NODE_IDENTITY}
    query ConditionalLogicCurrentNodeByPath($path: String!, $workspace: Workspace!, $language: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $path) {
                ...JcrNodeIdentity
                displayName(language: $language)
                primaryNodeType {
                    name
                }
                parent {
                    ...JcrNodeIdentity
                    primaryNodeType {
                        name
                    }
                    parent {
                        ...JcrNodeIdentity
                        primaryNodeType {
                            name
                        }
                        parent {
                            ...JcrNodeIdentity
                            primaryNodeType {
                                name
                            }
                            parent {
                                ...JcrNodeIdentity
                                primaryNodeType {
                                    name
                                }
                                parent {
                                    ...JcrNodeIdentity
                                    primaryNodeType {
                                        name
                                    }
                                    parent {
                                        ...JcrNodeIdentity
                                        primaryNodeType {
                                            name
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
`;

export const FORM_TREE_BY_PATH = gql`
    ${JCR_NODE_IDENTITY}
    query ConditionalLogicFormTreeByPath($path: String!, $workspace: Workspace!, $language: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $path) {
                ...JcrNodeIdentity
                children(names: ["fields"]) {
                    nodes {
                        ...JcrNodeIdentity
                        children {
                            nodes {
                                ...ConditionalLogicTreeNode
                            }
                        }
                    }
                }
            }
        }
    }

    fragment ConditionalLogicTreeNode on GenericJCRNode {
        ...JcrNodeIdentity
        displayName(language: $language)
        primaryNodeType {
            name
        }
        properties(names: ["choices", "options"], language: $language) {
            name
            value
            values
        }
        children {
            nodes {
                ...ConditionalLogicTreeNodeLevel2
            }
        }
    }

    fragment ConditionalLogicTreeNodeLevel2 on GenericJCRNode {
        ...JcrNodeIdentity
        displayName(language: $language)
        primaryNodeType {
            name
        }
        properties(names: ["choices", "options"], language: $language) {
            name
            value
            values
        }
        children {
            nodes {
                ...ConditionalLogicTreeNodeLevel3
            }
        }
    }

    fragment ConditionalLogicTreeNodeLevel3 on GenericJCRNode {
        ...JcrNodeIdentity
        displayName(language: $language)
        primaryNodeType {
            name
        }
        properties(names: ["choices", "options"], language: $language) {
            name
            value
            values
        }
        children {
            nodes {
                ...ConditionalLogicTreeNodeLevel4
            }
        }
    }

    fragment ConditionalLogicTreeNodeLevel4 on GenericJCRNode {
        ...JcrNodeIdentity
        displayName(language: $language)
        primaryNodeType {
            name
        }
        properties(names: ["choices", "options"], language: $language) {
            name
            value
            values
        }
        children {
            nodes {
                ...JcrNodeIdentity
                displayName(language: $language)
                primaryNodeType {
                    name
                }
                properties(names: ["choices", "options"], language: $language) {
                    name
                    value
                    values
                }
            }
        }
    }
`;

export const parseRule = (value?: string): ConditionalLogicRule => {
    if (!value) {
        return {
            sourceFieldId: '',
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
            sourceFieldId: parsed.sourceFieldId ?? '',
            sourceFieldName: parsed.sourceFieldName ?? '',
            sourceFieldType,
            operator: (parsed.operator as LogicOperator) ?? 'in',
            value: typeof parsed.value === 'string' ? parsed.value : undefined,
            values: Array.isArray(parsed.values) ? parsed.values.filter(value => typeof value === 'string') : []
        };
    } catch {
        return {
            sourceFieldId: '',
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
                sourceFieldId: source.id,
                sourceFieldName: source.name,
                sourceFieldType: source.type,
                operator,
                values: (rule.values ?? []).slice(0, 2)
            };
        }

        return {
            sourceFieldId: source.id,
            sourceFieldName: source.name,
            sourceFieldType: source.type,
            operator,
            value: rule.value ?? ''
        };
    }

    if (source.type === 'fmdb:checkbox' && source.choiceValues.length <= 1) {
        return {
            sourceFieldId: source.id,
            sourceFieldName: source.name,
            sourceFieldType: source.type,
            operator
        };
    }

    return {
        sourceFieldId: source.id,
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
    let current = node?.parent;
    while (current) {
        if (current.primaryNodeType?.name === 'fmdb:form') {
            return current.path;
        }

        current = current.parent ?? null;
    }

    return undefined;
};

const getNodeType = (node?: GraphNode | GraphParentNode | null): string | undefined => {
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

const flattenFormNodes = (nodes: GraphNode[] = []): GraphNode[] => {
    const flattened: GraphNode[] = [];

    const visit = (node: GraphNode) => {
        flattened.push(node);
        for (const child of node.children?.nodes ?? []) {
            visit(child);
        }
    };

    for (const node of nodes) {
        visit(node);
    }

    return flattened;
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

export const buildSourceFieldOptions = (currentNodeId: string, nodes: GraphNode[] = []): SourceFieldOption[] => {
    const flattened = flattenFormNodes(nodes);
    const currentIndex = flattened.findIndex(node => node.uuid === currentNodeId);
    if (currentIndex === -1) {
        return [];
    }

    return flattened
        .slice(0, currentIndex)
        .map(mapSourceField)
        .filter((node): node is SourceFieldOption => node !== null);
};
