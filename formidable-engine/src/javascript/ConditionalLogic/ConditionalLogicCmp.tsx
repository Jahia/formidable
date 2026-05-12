import {useApolloClient} from '@apollo/client';
import {Dropdown, Input, Loader, Typography} from '@jahia/moonstone';
import React, {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
    buildSourceFieldOptions,
    CURRENT_NODE_BY_PATH,
    extractCurrentNodePath,
    extractLanguage,
    extractWorkspace,
    findFormPath,
    FORM_TREE_BY_PATH,
    getOperatorsForSource,
    normalizeStoredRule,
    parseRule,
    sanitizeOperator
} from './ConditionalLogic.utils';
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

export const ConditionalLogicCmp = (props: SelectorProps) => {
    const {field, id, value, onChange} = props;
    const {t} = useTranslation('formidable-engine');
    const client = useApolloClient();
    const [sources, setSources] = useState<SourceFieldOption[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const currentNodePath = extractCurrentNodePath(props);
    const language = extractLanguage(props);
    const workspace = extractWorkspace(props);
    const rule = useMemo(() => parseRule(value), [value]);

    const siblingSourceIds = useMemo(() => {
        const allEntries = field.name ? props.form?.values?.[field.name] : undefined;
        if (!Array.isArray(allEntries)) {
            return new Set<string>();
        }

        return new Set(
            allEntries
                .filter((entry): entry is string => typeof entry === 'string' && entry !== value)
                .map(entry => parseRule(entry).sourceFieldId)
                .filter(sourceId => sourceId !== '')
        );
    }, [field.name, props.form?.values, value]);

    const availableSources = useMemo(
        () => sources.filter(source => source.id === rule.sourceFieldId || !siblingSourceIds.has(source.id)),
        [sources, siblingSourceIds, rule.sourceFieldId]
    );

    const selectedSource = useMemo(
        () => availableSources.find(source => source.id === rule.sourceFieldId),
        [rule.sourceFieldId, availableSources]
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

                const formTreeResult = await client.query<{
                    jcr?: {nodeByPath?: GraphNode | null} | null;
                }>({
                    query: FORM_TREE_BY_PATH,
                    variables: {path: formPath, workspace, language},
                    fetchPolicy: 'network-only'
                });

                const topLevelNodes = formTreeResult.data?.jcr?.nodeByPath?.children?.nodes?.[0]?.children?.nodes ?? [];
                if (!cancelled) {
                    setSources(buildSourceFieldOptions(currentNode.uuid, topLevelNodes));
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
        const source = sources.find(source => source.id === nextRule.sourceFieldId);
        onChange(JSON.stringify(normalizeStoredRule(nextRule, source)));
    };

    const handleSourceChange = (_event: React.MouseEvent, item: {value?: string}) => {
        const sourceId = item.value ?? '';
        const nextSource = sources.find(source => source.id === sourceId);
        if (!nextSource) {
            onChange(JSON.stringify(parseRule(undefined)));
            return;
        }

        const nextOperator = getOperatorsForSource(nextSource)[0];
        updateRule({
            sourceFieldId: nextSource.id,
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
            sourceFieldId: selectedSource.id,
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
            sourceFieldId: selectedSource.id,
            sourceFieldName: selectedSource.name,
            sourceFieldType: selectedSource.type,
            operator: selectedOperator,
            values: nextValues
        });
    };

    const sourceOptions = useMemo(
        () => availableSources.map(source => ({label: source.label, value: source.id})),
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
                    value={selectedSource?.id}
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
                            sourceFieldId: selectedSource.id,
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
