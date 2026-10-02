import {Dropdown, Input, Loader, Typography} from '@jahia/moonstone';
import React, {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
    extractCurrentNodePath,
    extractDefaultLanguage,
    extractLanguage,
    extractWorkspace,
    getOperatorsForSource,
    isScalarValueKind,
    clearedProviderConfig,
    normalizeStoredProviderRule,
    localIsoDay,
    normalizeStoredRule,
    parseRule,
    sanitizeProviderOperator,
    sanitizeOperator,
    TODAY_SENTINEL,
    withStoredValues
} from './ConditionalLogic.utils';
import {getSourceDescriptor, operatorNeedsValue} from './sourceDescriptors';
import {getLogicProvider, listLogicProviders, type LogicProviderDescriptor, PROVIDER_OPERATORS} from './providers';
import {useSharedSources} from './sources';
import {
    allRuleValuesOf,
    describeRuleView,
    isLastRuleOfList,
    providerReferenceIssue,
    providerRefErrorShown,
    rowMessageOf,
    ruleSourceTypeOf,
    translated
} from './ruleRow';
import type {ConditionalLogicRule, LogicOperator, RuleSourceType, SelectorProps} from './ConditionalLogic.types';
import './conditionalLogic.css';



/**
 * One scalar comparison value. Date values may also be the submission day: the
 * icon toggle stores the TODAY_SENTINEL instead of a fixed date, and the date
 * input is disabled while it does (a native date input cannot display the
 * sentinel).
 */
const ScalarValueInput = ({
    inputId,
    inputType,
    readOnly,
    placeholder,
    title,
    min,
    max,
    value,
    onValueChange
}: {
    inputId: string;
    inputType: 'date' | 'number' | 'text';
    readOnly?: boolean;
    placeholder: string;
    title?: string;
    min?: string;
    max?: string;
    value: string;
    onValueChange: (value: string) => void;
}) => {
    const {t} = useTranslation('formidable-engine');
    // The sentinel only exists for date inputs: a text rule comparing against the
    // literal string "today" must stay an ordinary editable value.
    const isToday = inputType === 'date' && value === TODAY_SENTINEL;

    // A free-text value can outgrow its cell: hovering reveals it in full. Dates
    // stay short, and their between inputs carry the start/end title instead.
    const hoverTitle = title ?? (inputType !== 'date' && value ? value : undefined);

    const input = (
        <Input
            id={inputId}
            type={inputType}
            isReadOnly={readOnly}
            isDisabled={isToday}
            placeholder={placeholder}
            title={hoverTitle}
            aria-label={title}
            min={min}
            max={max}
            value={isToday ? '' : value}
            onChange={event => onValueChange(event.target.value)}
            size="big"
        />
    );

    if (inputType !== 'date') {
        return input;
    }

    return (
        // The toggle is an icon block glued to the calendar's left edge (input-group
        // style), so the whole rule keeps one visual line. No visible text: the full
        // wording lives in the tooltip and the aria-label, the pressed state in the
        // accent background. The icon is a hand-drawn calendar with today's dot.
        <div className="flexRow_nowrap fmdbTodayGroup">
            <button
                type="button"
                data-sel-role="today-toggle"
                className={isToday ? 'fmdbTodayToggle fmdbTodayToggle_on' : 'fmdbTodayToggle'}
                title={t('conditionalLogic.valueToday')}
                aria-label={t('conditionalLogic.valueToday')}
                aria-pressed={isToday}
                disabled={readOnly}
                onClick={() => onValueChange(isToday ? '' : TODAY_SENTINEL)}
            >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <rect x="3" y="4" width="18" height="18" rx="2"/>
                    <path d="M8 2v4M16 2v4M3 10h18"/>
                    <circle cx="12" cy="16" r="1.6" fill="currentColor" stroke="none"/>
                </svg>
            </button>
            <div className="flexFluid">{input}</div>
        </div>
    );
};

const ScalarValueFields = ({
    id,
    inputType,
    readOnly,
    operator,
    rule,
    onChange
}: {
    id: string;
    inputType: 'date' | 'number' | 'text';
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

        // Each calendar steers away from an empty interval: the end picker starts
        // at the start date and vice versa. Both bounds are included, so equality
        // stays selectable — a one-day rule is the same date twice. A sentinel side
        // constrains the other by the contributor's own day (approximation at
        // authoring time; the runtime re-resolves it at submission).
        const isDate = inputType === 'date';
        const editDay = isDate ? localIsoDay() : '';
        const resolveBound = (bound: string) => (bound === TODAY_SENTINEL ? editDay : bound);
        const fromMax = isDate && values[1] ? resolveBound(values[1]) : undefined;
        const toMin = isDate && values[0] ? resolveBound(values[0]) : undefined;

        return (
            <div className="flexRow_nowrap" style={{gap: '0.5rem'}}>
                <div className="flexFluid">
                    <ScalarValueInput
                        inputId={`${id}-${inputType}-from`}
                        inputType={inputType}
                        readOnly={readOnly}
                        placeholder={t('conditionalLogic.valueFrom')}
                        title={isDate ? t('conditionalLogic.valueFromTitle') : undefined}
                        max={fromMax}
                        value={values[0]}
                        onValueChange={value => onChange({values: [value, values[1]]})}
                    />
                </div>
                <div className="flexFluid">
                    <ScalarValueInput
                        inputId={`${id}-${inputType}-to`}
                        inputType={inputType}
                        readOnly={readOnly}
                        placeholder={t('conditionalLogic.valueTo')}
                        title={isDate ? t('conditionalLogic.valueToTitle') : undefined}
                        min={toMin}
                        value={values[1]}
                        onValueChange={value => onChange({values: [values[0], value]})}
                    />
                </div>
            </div>
        );
    }

    return (
        <div>
            <ScalarValueInput
                inputId={`${id}-${inputType}-value`}
                inputType={inputType}
                readOnly={readOnly}
                placeholder={t('conditionalLogic.value')}
                value={rule.value ?? ''}
                onValueChange={value => onChange({value})}
            />
        </div>
    );
};

const generateLogicId = (): string => {
    return Math.random().toString(36).substring(2, 10);
};

// The Content Editor regenerates the React keys of every multiple-value row when an
// entry is added or removed (useReorderList assigns fresh uniqueIds), remounting the
// rule components and dropping their local state. The visited flag of the provider
// reference survives here, keyed by the rule's logicId — set as soon as a provider
// type is picked, and stable across remounts. Editor-session scoped by design.
const touchedProviderRefs = new Set<string>();

export const ConditionalLogicCmp = (props: SelectorProps) => {
    const {field, id, value, onChange} = props;
    const {t} = useTranslation('formidable-engine');
    const currentNodePath = extractCurrentNodePath(props);
    const language = extractLanguage(props);
    const workspace = extractWorkspace(props);
    // The site default language holds the option identity of choice fields; the dropdown is
    // labelled in the content language with it as fallback.
    const defaultLanguage = extractDefaultLanguage(props, language);
    // One load of the form per field being edited, shared by every rule row (sources.ts): the editor
    // mounts a row per rule and remounts them all when one is added, each needing the same form.
    const {sources, logicIdToSource, loading, errorKey} = useSharedSources(currentNodePath, {workspace, language, defaultLanguage});
    const error = translated(t, errorKey);
    const rule = useMemo(() => parseRule(value), [value]);
    // Whether the provider-reference input was visited: its error only shows after
    // that (a rule opened with a stored reference is validated right away). Declared
    // with the other hooks — the component has early returns further down.
    const [providerRefTouched, setProviderRefTouched] = useState(() => {
        const initialRule = parseRule(value);
        const provider = getLogicProvider(initialRule.sourceType);
        if (!provider) {
            return false;
        }

        return (initialRule[provider.configKey] ?? '').trim() !== ''
            || (Boolean(initialRule.logicId) && touchedProviderRefs.has(initialRule.logicId!));
    });

    // Resolve the selected source: sourceFieldKey is the business identity and wins,
    // then the stored UUID, then weakref resolution via logicId, then the legacy name.
    const resolvedSourceNodeId = useMemo(() => {
        if (rule.sourceFieldKey) {
            const match = sources.find(source => source.fieldKey === rule.sourceFieldKey);
            if (match) {
                return match.id;
            }
        }

        if (rule.sourceNodeId) {
            return rule.sourceNodeId;
        }

        if (rule.logicId) {
            const resolved = logicIdToSource.get(rule.logicId);
            if (resolved) {
                return resolved.uuid;
            }
        }

        // Legacy fallback: find source by name
        if (rule.sourceFieldName) {
            const match = sources.find(source => source.name === rule.sourceFieldName);
            if (match) {
                return match.id;
            }
        }

        return '';
    }, [rule, logicIdToSource, sources]);

    const siblingSourceNodeIds = useMemo(() => {
        const allEntries = field.name ? props.form?.values?.[field.name] : undefined;
        if (!Array.isArray(allEntries)) {
            return new Set<string>();
        }

        return new Set(
            allEntries
                .filter((entry): entry is string => typeof entry === 'string' && entry !== value)
                .map(entry => {
                    const siblingRule = parseRule(entry);
                    if (siblingRule.sourceFieldKey) {
                        const match = sources.find(s => s.fieldKey === siblingRule.sourceFieldKey);
                        if (match) {
                            return match.id;
                        }
                    }

                    if (siblingRule.sourceNodeId) {
                        return siblingRule.sourceNodeId;
                    }

                    if (siblingRule.logicId) {
                        const resolved = logicIdToSource.get(siblingRule.logicId);
                        if (resolved) {
                            return resolved.uuid;
                        }
                    }

                    // Legacy fallback
                    if (siblingRule.sourceFieldName) {
                        const match = sources.find(s => s.name === siblingRule.sourceFieldName);
                        if (match) {
                            return match.id;
                        }
                    }

                    return '';
                })
                .filter(id => id !== '')
        );
    }, [field.name, props.form?.values, value, logicIdToSource, sources]);

    const availableSources = useMemo(
        () => sources.filter(source =>
            source.id === resolvedSourceNodeId
            || !siblingSourceNodeIds.has(source.id)),
        [sources, siblingSourceNodeIds, resolvedSourceNodeId]
    );

    const selectedSource = useMemo(
        () => availableSources.find(source => source.id === resolvedSourceNodeId),
        [resolvedSourceNodeId, availableSources]
    );
    const selectedDescriptor = getSourceDescriptor(selectedSource?.type, selectedSource?.valueKind);
    const selectedOperator = sanitizeOperator(selectedSource, rule.operator);

    const updateRule = (nextRule: ConditionalLogicRule) => {
        const source = sources.find(source => source.id === nextRule.sourceNodeId);
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
        const nextIsScalar = isScalarValueKind(getSourceDescriptor(nextSource.type, nextSource.valueKind)?.valueKind);
        const logicId = rule.logicId || generateLogicId();
        updateRule({
            logicId,
            sourceNodeId: nextSource.id,
            sourceFieldName: nextSource.name,
            sourceFieldType: nextSource.type,
            operator: nextOperator,
            value: nextIsScalar ? '' : undefined,
            values: nextIsScalar && nextOperator === 'between' ? ['', ''] : []
        });
    };

    const handleOperatorChange = (_event: React.MouseEvent, item: {value?: string}) => {
        if (!selectedSource || !item.value) {
            return;
        }

        const operator = item.value as LogicOperator;
        const isScalar = isScalarValueKind(selectedDescriptor?.valueKind);
        updateRule({
            ...rule,
            logicId: rule.logicId || generateLogicId(),
            sourceNodeId: selectedSource.id,
            sourceFieldName: selectedSource.name,
            sourceFieldType: selectedSource.type,
            operator,
            value: isScalar && operator !== 'between' ? (rule.value ?? '') : undefined,
            values: isScalar && operator === 'between'
                ? (rule.values ?? ['', '']).slice(0, 2)
                : (isScalar ? [] : (rule.values ?? []))
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
            sourceNodeId: selectedSource.id,
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
    // Not memoized: the list is tiny, and rule.values must take part — a stored value
    // the current language's list does not know still needs its (raw-value) chip.
    const valueOptions = withStoredValues(
        (selectedSource?.choiceValues ?? []).map(choice => ({label: choice.label, value: choice.value})),
        rule.values ?? []
    );

    // Which value control the row shows (ruleRow.ts): the choices of a choice source, a typed input for a
    // scalar one, nothing for an operator such as "is filled".
    const view = describeRuleView(selectedSource, selectedDescriptor, selectedOperator);
    const {showValueDropdown, showScalarInput, scalarInputType} = view;

    const selectedProvider = getLogicProvider(rule.sourceType);
    // A sourceType naming no provider this module ships comes from a rule authored
    // against a newer version: it gets its own rendering below, never the field-rule UI,
    // and keeps its raw sourceType so nothing rewrites the stored rule.
    const {isUnknownSourceType, ruleSourceType} = ruleSourceTypeOf(rule, selectedProvider);
    const providerOperator = sanitizeProviderOperator(rule.operator);

    const handleSourceTypeChange = (_event: React.MouseEvent, item: {value?: string}) => {
        const nextType = (item.value ?? 'field') as RuleSourceType;
        if (nextType === ruleSourceType) {
            return;
        }

        const nextProvider = getLogicProvider(nextType);
        setProviderRefTouched(false);
        if (rule.logicId) {
            touchedProviderRefs.delete(rule.logicId);
        }

        if (nextProvider) {
            onChange(JSON.stringify(normalizeStoredProviderRule({
                ...rule,
                ...clearedProviderConfig(),
                logicId: rule.logicId || generateLogicId(),
                sourceType: nextProvider.id,
                [nextProvider.configKey]: '',
                operator: PROVIDER_OPERATORS[0],
                value: ''
            })));
            return;
        }

        onChange(JSON.stringify(parseRule(undefined)));
    };

    const updateProviderRule = (patch: Partial<ConditionalLogicRule>) => {
        onChange(JSON.stringify(normalizeStoredProviderRule({
            ...rule,
            logicId: rule.logicId || generateLogicId(),
            operator: providerOperator,
            ...patch
        })));
    };

    const sourceTypeOptions = [
        {label: t('conditionalLogic.sourceTypes.field'), value: 'field'},
        ...listLogicProviders().map(provider => ({label: t(provider.labelKey), value: provider.id}))
    ];
    const providerOperatorOptions = PROVIDER_OPERATORS.map(operator => ({
        label: t(`conditionalLogic.operators.${operator}`),
        value: operator
    }));

    if (loading) {
        return <Loader size="small"/>;
    }

    const renderFieldRule = () => {
        if (error) {
            return (
                <div className="fmdbRuleSpan">
                    <Typography variant="body" style={{color: 'var(--color-danger)'}}>{error}</Typography>
                </div>
            );
        }

        if (sources.length === 0) {
            return (
                <div className="fmdbRuleSpan">
                    <Typography variant="body" style={{color: 'var(--color-gray)'}}>
                        {t('conditionalLogic.noSources')}
                    </Typography>
                </div>
            );
        }

        if (availableSources.length === 0) {
            return (
                <div className="fmdbRuleSpan">
                    <Typography variant="body" style={{color: 'var(--color-gray)'}}>
                        {t('conditionalLogic.allSourcesUsed')}
                    </Typography>
                </div>
            );
        }

        return (
            <>
                <div>
                    <Dropdown
                        variant="outlined"
                        data={sourceOptions}
                        hasSearch={sourceOptions.length >= 5}
                        value={selectedSource?.id}
                        placeholder={t('conditionalLogic.selectSource')}
                        isDisabled={field.readOnly}
                        onChange={handleSourceChange}
                    />
                </div>
                <div>
                    <Dropdown
                        variant="outlined"
                        data={operatorOptions}
                        hasSearch={operatorOptions.length >= 5}
                        value={selectedOperator}
                        placeholder={t('conditionalLogic.operator')}
                        isDisabled={field.readOnly || !selectedSource}
                        onChange={handleOperatorChange}
                    />
                </div>

                {showValueDropdown && (
                    <div>
                        <Dropdown
                            variant="outlined"
                            data={valueOptions}
                            hasSearch={valueOptions.length >= 5}
                            values={rule.values ?? []}
                            placeholder={t('conditionalLogic.values')}
                            isDisabled={field.readOnly}
                            onChange={handleValuesChange}
                        />
                    </div>
                )}

                {!showValueDropdown && !showScalarInput && (
                    <div/>
                )}

                {showScalarInput && selectedSource && (
                    <div>
                        <ScalarValueFields
                            id={id}
                            inputType={scalarInputType}
                            readOnly={field.readOnly}
                            operator={selectedOperator}
                            rule={rule}
                            onChange={patch => updateRule({
                                ...rule,
                                logicId: rule.logicId || generateLogicId(),
                                sourceNodeId: selectedSource.id,
                                sourceFieldName: selectedSource.name,
                                sourceFieldType: selectedSource.type,
                                operator: selectedOperator,
                                ...patch
                            })}
                        />
                    </div>
                )}
            </>
        );
    };

    // A rule authored against a newer module version (unknown sourceType) is shown as-is
    // and never serialized from here: nothing in this branch fires onChange, so the
    // stored JSON round-trips unchanged. The runtime fails such rules closed (field
    // hidden, wrapper flagged) — this is the authoring-side mirror of that stance.
    // Picking another source type in the dropdown remains possible and is a deliberate
    // rewrite by the contributor.
    const renderUnknownSourceRule = () => (
        <div className="fmdbRuleSpan">
            <Typography variant="body" style={{color: 'var(--color-danger)'}}>
                {t('conditionalLogic.unknownSourceType', {type: rule.sourceType})}
            </Typography>
        </div>
    );

    // Problems are surfaced here rather than blocked: an invalid reference can never
    // be read on the page (the rule fails closed and hides the field), and a rule
    // left with an empty reference is removed server-side when the element is saved
    // (FormLogicRuleCleanup). The message lives in a reserved line below the whole
    // row, so its presence never moves the fields, and only shows once the
    // contributor has left the reference input (a rule opened with a stored
    // reference is validated right away).
    const providerRefIssue = providerReferenceIssue(selectedProvider, rule);
    // A rule that is no longer the last of the list has been left behind: the contributor added rules
    // after it, so its reference counts as visited even when no blur was ever observed (ruleRow.ts).
    const showProviderRefError = providerRefErrorShown(
        providerRefIssue,
        providerRefTouched,
        isLastRuleOfList(id, allRuleValuesOf(props.form, field.name))
    );

    // One shape for every provider: the name of the thing designated, an operator, and a
    // value when the operator compares against one. Adding a provider adds no markup here.
    const renderProviderRule = (provider: LogicProviderDescriptor) => {
        return (
        <>
            <div>
                <Input
                    id={`${id}-provider-ref`}
                    isReadOnly={field.readOnly}
                    placeholder={t(provider.configPlaceholderKey)}
                    // A reference easily outgrows its cell: hovering reveals it in full.
                    title={(rule[provider.configKey] ?? '') || undefined}
                    aria-label={t(provider.configLabelKey)}
                    aria-invalid={showProviderRefError ? true : undefined}
                    aria-describedby={showProviderRefError ? `${id}-provider-ref-error` : undefined}
                    value={rule[provider.configKey] ?? ''}
                    size="big"
                    className={showProviderRefError ? 'fmdbProviderRefError' : undefined}
                    onChange={event => updateProviderRule({[provider.configKey]: event.target.value})}
                    onBlur={() => {
                        setProviderRefTouched(true);
                        if (rule.logicId) {
                            touchedProviderRefs.add(rule.logicId);
                        }
                    }}
                />
            </div>
            <div>
                <Dropdown
                    variant="outlined"
                    data={providerOperatorOptions}
                    hasSearch={providerOperatorOptions.length >= 5}
                    value={providerOperator}
                    placeholder={t('conditionalLogic.operator')}
                    isDisabled={field.readOnly}
                    onChange={(_event, item) => {
                        if (item.value) {
                            updateProviderRule({operator: item.value as LogicOperator});
                        }
                    }}
                />
            </div>
            {operatorNeedsValue(providerOperator) ? (
                <div>
                    <Input
                        id={`${id}-provider-value`}
                        isReadOnly={field.readOnly}
                        placeholder={t('conditionalLogic.value')}
                        title={(rule.value ?? '') || undefined}
                        value={rule.value ?? ''}
                        size="big"
                        onChange={event => updateProviderRule({value: event.target.value})}
                    />
                </div>
            ) : (
                <div/>
            )}
        </>
        );
    };

    // Leaving the rule row counts as visiting the reference: a contributor who picks a
    // provider type and moves on without ever entering the reference input still gets
    // the error. Focus events bubble, and moving between the row's own inputs keeps
    // relatedTarget inside the row, so nothing fires while the rule is being edited.
    const handleRowBlur = (event: React.FocusEvent<HTMLDivElement>) => {
        if (!selectedProvider || providerRefTouched) {
            return;
        }

        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
            setProviderRefTouched(true);
            if (rule.logicId) {
                touchedProviderRefs.add(rule.logicId);
            }
        }
    };

    // Date interval coherence, surfaced on the same reserved line as the provider
    // reference errors. 'inverted' (two fixed dates in the wrong order) is an
    // authoring error; 'noMatch' (a submission-day side empties the interval) is a
    // warning — the runtime ignores such a rule instead of hiding its field forever.
    const row = rowMessageOf({
        providerIssueShown: showProviderRefError,
        providerIssue: providerRefIssue,
        isFieldRule: !selectedProvider,
        view,
        operator: selectedOperator,
        values: rule.values
    });
    const rowMessage = translated(t, row.messageKey);

    const renderRuleBody = () => {
        if (selectedProvider) {
            return renderProviderRule(selectedProvider);
        }

        return isUnknownSourceType ? renderUnknownSourceRule() : renderFieldRule();
    };

    return (
        <div className="flexCol flexFluid" onBlur={handleRowBlur}>
            <div className="fmdbRuleGrid" data-sel-role="logic-rule">
                <div>
                    <Dropdown
                        variant="outlined"
                        data={sourceTypeOptions}
                        hasSearch={sourceTypeOptions.length >= 5}
                        value={ruleSourceType}
                        isDisabled={field.readOnly}
                        onChange={handleSourceTypeChange}
                    />
                </div>
                {renderRuleBody()}
            </div>
            {/* Reserved line: keeps every rule row the same height whether or not a
                message is shown, so it never shifts the fields around. */}
            <Typography
                id={`${id}-provider-ref-error`}
                variant="caption"
                style={{
                    minHeight: '1.25rem',
                    color: row.color,
                    visibility: row.visibility
                }}
            >
                {rowMessage ?? ''}
            </Typography>
        </div>
    );
};

ConditionalLogicCmp.displayName = 'ConditionalLogicCmp';
