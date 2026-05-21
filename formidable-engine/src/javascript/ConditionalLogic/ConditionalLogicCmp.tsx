import {useApolloClient} from '@apollo/client';
import {Dropdown, Input, Loader, Typography} from '@jahia/moonstone';
import React, {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
    buildLogicIdToNameMap,
    buildSourceFieldOptions,
    extractCurrentNodePath,
    extractLanguage,
    extractWorkspace,
    findFormPath,
    getOperatorsForSource,
    normalizeStoredRule,
    parseRule,
    sanitizeOperator
} from './ConditionalLogic.utils';
import {CURRENT_NODE_BY_PATH, FORM_TREE_BY_PATH} from './graphql';
import type {ConditionalLogicRule, GraphNode, LogicOperator, SelectorProps, SourceFieldOption} from './ConditionalLogic.types';



const DateValueFields = ({
    id,
    readOnly,
    operator,
    rule,
    onChange
}: {
    id: string;
    readOnly?: boolean;
    operator: LogicOperator;
    rule: ConditionalLogicRule;
    onChange: (patch: Partial<ConditionalLogicRule>) => void;
}) => {
    const {t} = useTranslation('formidable-engine');

    if (operator === 'between') {
        const values = (rule.values ?? ['', '']).slice(0, 2);
        while (values.length < 2) {
            values.push('');
        }

        return (
            <div className="flexRow_nowrap" style={{gap: '0.5rem'}}>
                <div className="flexFluid">
                    <Input
                        id={`${id}-date-from`}
                        type="date"
                        isReadOnly={readOnly}
                        placeholder={t('conditionalLogic.dateFrom')}
                        value={values[0]}
                        onChange={event => onChange({values: [event.target.value, values[1]]})}
                        size="big"
                    />
                </div>
                <div className="flexFluid">
                    <Input
                        id={`${id}-date-to`}
                        type="date"
                        isReadOnly={readOnly}
                        placeholder={t('conditionalLogic.dateTo')}
                        value={values[1]}
                        onChange={event => onChange({values: [values[0], event.target.value]})}
                        size="big"
                    />
                </div>
            </div>
        );
    }

    return (
        <div>
            <Input
                id={`${id}-date-value`}
                type="date"
                isReadOnly={readOnly}
                placeholder={t('conditionalLogic.value')}
                value={rule.value ?? ''}
                onChange={event => onChange({value: event.target.value})}
                size="big"
            />
        </div>
    );
};

const generateLogicId = (): string => {
    return Math.random().toString(36).substring(2, 10);
};

export const ConditionalLogicCmp = (props: SelectorProps) => {
    const {field, id, value, onChange} = props;
    const {t} = useTranslation('formidable-engine');
    const client = useApolloClient();
    const [sources, setSources] = useState<SourceFieldOption[]>([]);
    const [logicIdToName, setLogicIdToName] = useState<Map<string, string>>(new Map());
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const currentNodePath = extractCurrentNodePath(props);
    const language = extractLanguage(props);
    const workspace = extractWorkspace(props);
    const rule = useMemo(() => parseRule(value), [value]);

    // Resolve sourceFieldName via weakref: if the source field was renamed,
    // the logicId → weakref → refNode.name gives the current name.
    const resolvedSourceFieldName = useMemo(() => {
        if (rule.logicId) {
            const resolved = logicIdToName.get(rule.logicId);
            if (resolved) {
                return resolved;
            }
        }

        return rule.sourceFieldName;
    }, [rule, logicIdToName]);

    const siblingSourceNames = useMemo(() => {
        const allEntries = field.name ? props.form?.values?.[field.name] : undefined;
        if (!Array.isArray(allEntries)) {
            return new Set<string>();
        }

        return new Set(
            allEntries
                .filter((entry): entry is string => typeof entry === 'string' && entry !== value)
                .map(entry => {
                    const siblingRule = parseRule(entry);
                    if (siblingRule.logicId) {
                        return logicIdToName.get(siblingRule.logicId) ?? siblingRule.sourceFieldName;
                    }

                    return siblingRule.sourceFieldName;
                })
                .filter(sourceName => sourceName !== '')
        );
    }, [field.name, props.form?.values, value, logicIdToName]);

    const availableSources = useMemo(
        () => sources.filter(source =>
            source.name === resolvedSourceFieldName
            || !siblingSourceNames.has(source.name)),
        [sources, siblingSourceNames, resolvedSourceFieldName]
    );

    const selectedSource = useMemo(
        () => availableSources.find(source => source.name === resolvedSourceFieldName),
        [resolvedSourceFieldName, availableSources]
    );
    const selectedOperator = sanitizeOperator(selectedSource, rule.operator);

    useEffect(() => {
        let cancelled = false;

        const loadSources = async () => {
            if (!currentNodePath) {
                setSources([]);
                setError(t('conditionalLogic.unresolvedContext'));
                return;
            }

            setLoading(true);
            setError(null);

            try {
                const currentNodeResult = await client.query<{
                    jcr?: {nodeByPath?: GraphNode | null} | null;
                }>({
                    query: CURRENT_NODE_BY_PATH,
                    variables: {path: currentNodePath, workspace, language},
                    fetchPolicy: 'network-only'
                });

                const currentNode = currentNodeResult.data?.jcr?.nodeByPath;
                const formPath = findFormPath(currentNode);
                if (!currentNode || !formPath) {
                    throw new Error(t('conditionalLogic.formNotFound'));
                }

                const logicSrcNodes = currentNode.descendant?.children?.nodes ?? [];
                const resolvedMap = buildLogicIdToNameMap(logicSrcNodes);

                const formTreeResult = await client.query<{
                    jcr?: {nodeByPath?: GraphNode | null} | null;
                }>({
                    query: FORM_TREE_BY_PATH,
                    variables: {path: formPath, workspace, language},
                    fetchPolicy: 'network-only'
                });

                const descendantNodes = formTreeResult.data?.jcr?.nodeByPath?.descendants?.nodes ?? [];
                if (!cancelled) {
                    setSources(buildSourceFieldOptions(currentNode.path, descendantNodes));
                    setLogicIdToName(resolvedMap);
                }
            } catch (error) {
                if (!cancelled) {
                    console.error('[ConditionalLogicCmp] failed to load source fields', error);
                    setSources([]);
                    setError(t('conditionalLogic.loadError'));
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };

        void loadSources();

        return () => {
            cancelled = true;
        };
    }, [client, currentNodePath, language, t, workspace]);

    const updateRule = (nextRule: ConditionalLogicRule) => {
        const source = sources.find(source => source.name === nextRule.sourceFieldName);
        onChange(JSON.stringify(normalizeStoredRule(nextRule, source)));
    };

    const handleSourceChange = (_event: React.MouseEvent, item: {value?: string}) => {
        const sourceName = item.value ?? '';
        const nextSource = sources.find(source => source.name === sourceName);
        if (!nextSource) {
            onChange(JSON.stringify(parseRule(undefined)));
            return;
        }

        const nextOperator = getOperatorsForSource(nextSource)[0];
        const logicId = rule.logicId || generateLogicId();
        updateRule({
            logicId,
            sourceFieldName: nextSource.name,
            sourceFieldType: nextSource.type,
            operator: nextOperator,
            value: nextSource.type === 'fmdb:inputDate' ? '' : undefined,
            values: nextSource.type === 'fmdb:inputDate' && nextOperator === 'between' ? ['', ''] : []
        });
    };

    const handleOperatorChange = (_event: React.MouseEvent, item: {value?: string}) => {
        if (!selectedSource || !item.value) {
            return;
        }

        const operator = item.value as LogicOperator;
        updateRule({
            ...rule,
            logicId: rule.logicId || generateLogicId(),
            sourceFieldName: selectedSource.name,
            sourceFieldType: selectedSource.type,
            operator,
            value: selectedSource.type === 'fmdb:inputDate' && operator !== 'between' ? (rule.value ?? '') : undefined,
            values: selectedSource.type === 'fmdb:inputDate' && operator === 'between'
                ? (rule.values ?? ['', '']).slice(0, 2)
                : (selectedSource.type === 'fmdb:inputDate' ? [] : (rule.values ?? []))
        });
    };

    const handleValuesChange = (_event: React.MouseEvent, item: {value?: string}) => {
        if (!selectedSource || !item.value) {
            return;
        }

        const currentValues = rule.values ?? [];
        const nextValues = currentValues.includes(item.value)
            ? currentValues.filter(value => value !== item.value)
            : [...currentValues, item.value];

        updateRule({
            ...rule,
            logicId: rule.logicId || generateLogicId(),
            sourceFieldName: selectedSource.name,
            sourceFieldType: selectedSource.type,
            operator: selectedOperator,
            values: nextValues
        });
    };

    const sourceOptions = useMemo(
        () => availableSources.map(source => ({label: source.label, value: source.name})),
        [availableSources]
    );
    const operatorOptions = useMemo(
        () => getOperatorsForSource(selectedSource).map(operator => ({
            label: t(`conditionalLogic.operators.${operator}`),
            value: operator
        })),
        [selectedSource, t]
    );
    const valueOptions = useMemo(
        () => (selectedSource?.choiceValues ?? []).map(choice => ({label: choice.label, value: choice.value})),
        [selectedSource]
    );

    const showValueDropdown = selectedSource
        && selectedSource.type !== 'fmdb:inputDate'
        && !(selectedSource.type === 'fmdb:checkbox' && selectedSource.choiceValues.length <= 1);

    if (loading) {
        return <Loader size="small"/>;
    }

    if (error) {
        return <Typography variant="body" style={{color: 'var(--color-danger)'}}>{error}</Typography>;
    }

    if (sources.length === 0) {
        return (
            <Typography variant="body" style={{color: 'var(--color-gray)'}}>
                {t('conditionalLogic.noSources')}
            </Typography>
        );
    }

    if (availableSources.length === 0) {
        return (
            <Typography variant="body" style={{color: 'var(--color-gray)'}}>
                {t('conditionalLogic.allSourcesUsed')}
            </Typography>
        );
    }

    return (
        <div className="flexRow_nowrap flexFluid alignCenter" style={{gap: '0.75rem'}}>
            <div className="flexFluid">
                <Dropdown
                  size="big"
                    data={sourceOptions}
                    value={selectedSource?.name}
                    placeholder={t('conditionalLogic.selectSource')}
                    isDisabled={field.readOnly}
                    onChange={handleSourceChange}
                />
            </div>
            <div className="flexFluid">
                <Dropdown
                  size="big"
                    data={operatorOptions}
                    value={selectedOperator}
                    placeholder={t('conditionalLogic.operator')}
                    isDisabled={field.readOnly || !selectedSource}
                    onChange={handleOperatorChange}
                />
            </div>

            {showValueDropdown && (
                <div className="flexFluid">
                    <Dropdown
                      size="big"
                        data={valueOptions}
                        values={rule.values ?? []}
                        placeholder={t('conditionalLogic.values')}
                        isDisabled={field.readOnly}
                        onChange={handleValuesChange}
                    />
                </div>
            )}

            {!showValueDropdown && selectedSource?.type !== 'fmdb:inputDate' && (
                <div className="flexFluid"/>
            )}

            {selectedSource?.type === 'fmdb:inputDate' && (
                <div className="flexFluid">
                    <DateValueFields
                        id={id}
                        readOnly={field.readOnly}
                        operator={selectedOperator}
                        rule={rule}
                        onChange={patch => updateRule({
                            ...rule,
                            logicId: rule.logicId || generateLogicId(),
                            sourceFieldName: selectedSource.name,
                            sourceFieldType: selectedSource.type,
                            operator: selectedOperator,
                            ...patch
                        })}
                    />
                </div>
            )}
        </div>
    );
};

ConditionalLogicCmp.displayName = 'ConditionalLogicCmp';
