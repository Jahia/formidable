/**
 * Shape of the values a source field produces, which drives the offered operators
 * and the value widget shown next to them. Declared by the field type through the
 * engine's semantic mixins (fmdbmix:choiceField, dateField, numberField,
 * booleanField, textField).
 */
export type SourceValueKind = 'choice' | 'date' | 'number' | 'boolean' | 'text';

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
    | 'eq'
    | 'neq'
    | 'lt'
    | 'lte'
    | 'gt'
    | 'gte'
    | 'isTrue'
    | 'isFalse'
    | 'isEmpty'
    | 'isNotEmpty'
    | 'equals'
    | 'notEquals'
    | 'contains'
    | 'exists'
    | 'notExists';

export interface ConditionalLogicRule {
    logicId: string;
    // Absent on rules stored before jsVariable support; treated as 'field'. It is `field`, or
    // the id of a provider, built in or registered by another module (providers.ts). A stored rule
    // may name a provider no module registers here (authored against a newer version, or
    // with a module since removed), and it must keep that sourceType
    // verbatim so the rule round-trips unchanged instead of being rewritten as a field
    // rule on save.
    sourceType?: string;
    // Field-rule keys; never serialized on jsVariable rules.
    sourceNodeId?: string;
    // Stable business reference to the source field (its fieldKey); primary
    // resolution criterion. Absent on rules stored before fieldKey existed.
    sourceFieldKey?: string;
    sourceFieldName?: string;
    sourceFieldType?: string;
    // Denormalized value kind of the source at authoring time; lets the runtime
    // evaluators pick the right comparison semantics (e.g. numeric vs date
    // 'between') without knowing the source type. Absent on older rules.
    valueKind?: SourceValueKind;
    operator: LogicOperator;
    value?: string;
    values?: string[];
    // Provider-rule config, exactly one key per rule, named by the provider's configKey: a dotted
    // window variable path (e.g. a datalayer entry), a URL query parameter name, a cookie name, or
    // what a registered provider designates. Read it with providerRef.
    [configKey: string]: string | string[] | undefined;
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
    /** Site info the Content Editor resolves before selectors mount. */
    siteInfo?: {
        defaultLanguage?: string;
    };
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
    // Semantic-mixin flags fetched by ConditionalLogicSources; drive source eligibility.
    isChoiceField?: boolean;
    isDateField?: boolean;
    isNumberField?: boolean;
    isBooleanField?: boolean;
    isTextField?: boolean;
    properties?: PropertyValue[] | null;
    defaultProperties?: PropertyValue[] | null;
    site?: {defaultLanguage?: string | null; installedModules?: string[] | null} | null;
    ancestors?: GraphAncestorNode[] | null;
    descendants?: {nodes?: GraphNode[] | null} | null;
    descendant?: {children?: {nodes?: LogicSrcNode[] | null} | null} | null;
}

export interface GraphAncestorNode {
    uuid: string;
    name: string;
    path: string;
    primaryNodeType?: {name?: string | null} | null;
    // The form tree, fetched under the fmdb:form ancestor by ConditionalLogicSources.
    descendants?: {nodes?: GraphNode[] | null} | null;
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
    // Declared by the field type's semantic mixin; undefined when the type
    // carries none (the field is then not eligible as a source).
    valueKind?: SourceValueKind;
    choiceValues: ChoiceValue[];
}
