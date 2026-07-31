export type RuleSourceType = 'field' | 'jsVariable';

export type LogicOperator =
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
    logicId: string;
    // Absent on rules stored before jsVariable support; treated as 'field'.
    sourceType?: RuleSourceType;
    // Field-rule keys; never serialized on jsVariable rules.
    sourceNodeId?: string;
    // Stable business reference to the source field (its fieldKey); primary
    // resolution criterion. Absent on rules stored before fieldKey existed.
    sourceFieldKey?: string;
    sourceFieldName?: string;
    sourceFieldType?: string;
    // jsVariable-rule key: dotted window variable path (e.g. a datalayer entry).
    variable?: string;
    operator: LogicOperator;
    value?: string;
    values?: string[];
}

export interface SelectorField {
    name?: string;
    readOnly?: boolean;
    node?: {path?: string; uuid?: string};
    path?: string;
    nodePath?: string;
}

export interface EditorContextLike {
    path?: string;
    uuid?: string;
    lang?: string;
    language?: string;
    uilang?: string;
    locale?: string;
    workspace?: string;
    nodeData?: {
        path?: string;
        uuid?: string;
        lang?: string;
        language?: string;
        workspace?: string;
    };
}

export interface SelectorProps {
    field: SelectorField;
    id: string;
    value?: string;
    readOnly?: boolean;
    onChange: (value: string) => void;
    editorContext?: EditorContextLike;
    context?: EditorContextLike;
    form?: {
        values?: Record<string, unknown>;
    };
}

export interface PropertyValue {
    name: string;
    value?: string | null;
    values?: string[] | null;
}

export interface LogicSrcNode {
    name: string;
    property?: {refNode?: {name: string; uuid: string} | null} | null;
}

export interface GraphNode {
    uuid: string;
    name: string;
    path: string;
    displayName?: string | null;
    primaryNodeType?: {name?: string | null} | null;
    properties?: PropertyValue[] | null;
    ancestors?: GraphAncestorNode[] | null;
    descendants?: {nodes?: GraphNode[] | null} | null;
    descendant?: {children?: {nodes?: LogicSrcNode[] | null} | null} | null;
}

export interface GraphAncestorNode {
    uuid: string;
    name: string;
    path: string;
    primaryNodeType?: {name?: string | null} | null;
}

export interface ChoiceValue {
    value: string;
    label: string;
}

export interface SourceFieldOption {
    id: string;
    // Stable business identity of the field; assigned server-side, may be briefly
    // absent on a field that has never been saved through the engine listeners.
    fieldKey?: string;
    name: string;
    path: string;
    label: string;
    type: string;
    choiceValues: ChoiceValue[];
}
