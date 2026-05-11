export type SupportedSourceType = 'fmdb:select' | 'fmdb:radio' | 'fmdb:checkbox' | 'fmdb:inputDate';

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
    | 'between';

export interface ConditionalLogicRule {
    sourceFieldId: string;
    sourceFieldName: string;
    sourceFieldType: SupportedSourceType;
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
    onChange: (value: string) => void;
    editorContext?: EditorContextLike;
    context?: EditorContextLike;
    formik?: {
        values?: Record<string, unknown>;
    };
}

export interface PropertyValue {
    name: string;
    value?: string | null;
    values?: string[] | null;
}

export interface GraphNode {
    uuid: string;
    name: string;
    path: string;
    displayName?: string | null;
    primaryNodeType?: {name?: string | null} | null;
    properties?: PropertyValue[] | null;
    children?: {nodes?: GraphNode[] | null} | null;
    parent?: GraphParentNode | null;
}

export interface GraphParentNode {
    uuid: string;
    name: string;
    path: string;
    primaryNodeType?: {name?: string | null} | null;
    parent?: GraphParentNode | null;
}

export interface SourceFieldOption {
    id: string;
    name: string;
    path: string;
    label: string;
    type: SupportedSourceType;
    choiceValues: string[];
}
