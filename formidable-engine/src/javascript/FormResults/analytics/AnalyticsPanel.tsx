import React, {useEffect, useMemo, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {Button, Download, Dropdown, Loader, Paper, Typography} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import {GET_SUBMISSIONS_FOR_ANALYTICS} from '../graphql';
import {buildSubmissionsQuery, formatDate, formResultsLabel, uiContext, type FormResultsNode} from '../FormResults.utils';
import {downloadFile, sanitizeFilenamePart} from '../export/export.utils';
import {
    ANALYTICS_PERIODS,
    buildAnalytics,
    isPie,
    parseAnalyticsFields,
    parseAnalyticsSubmission,
    periodStart,
    toDateValue,
    type AnalyticsPeriod,
    type AnalyticsSubmission,
    type FieldDistribution
} from './analytics.utils';
import {buildAnalyticsCsv, buildAnalyticsJson, type AnalyticsExportMeta} from './analytics.export';
import {BarList, formatCount, PIE_COLORS, PieChart, TimelineChart, type BarItem} from './charts';

interface AnalyticsPanelProps {
    formResults: FormResultsNode;
    siteKey: string;
    /** The answer of GET_FORM_FIELD_LABELS for the form, undefined while it loads or when the form is gone. */
    fieldLabelsData: Parameters<typeof parseAnalyticsFields>[0];
    onRegisterRefresh: (refresh: (() => Promise<unknown>) | null) => void;
}

const BATCH_SIZE = 200;
/**
 * The analytics are computed in the browser: past this many submissions of the period the newest are kept
 * and the page says so, rather than loading without end.
 */
const MAX_SUBMISSIONS = 10000;
const MUTED = 'var(--color-gray_dark)';

type LoadState =
    | {status: 'loading'; loaded: number; total: number}
    | {status: 'ready'; submissions: AnalyticsSubmission[]; total: number; now: Date}
    | {status: 'error'; message: string};

const LOADING: LoadState = {status: 'loading', loaded: 0, total: 0};

const Card = ({title, hint, children, style}: {title: string; hint?: string; children: React.ReactNode; style?: React.CSSProperties}) => (
    <Paper style={{margin: 0, minWidth: 0, display: 'flex', flexDirection: 'column', gap: '12px', ...style}}>
        <div>
            <Typography variant="subheading" weight="bold">{title}</Typography>
            {hint && <Typography variant="caption" style={{color: MUTED}}>{hint}</Typography>}
        </div>
        {children}
    </Paper>
);

const Kpi = ({label, value}: {label: string; value: string}) => (
    <Paper style={{margin: 0, minWidth: 0}}>
        <Typography variant="caption" style={{color: MUTED}}>{label}</Typography>
        <Typography variant="heading" weight="bold" style={{overflowWrap: 'anywhere'}}>{value}</Typography>
    </Paper>
);

export const AnalyticsPanel = ({formResults, siteKey, fieldLabelsData, onRegisterRefresh}: AnalyticsPanelProps) => {
    const client = useApolloClient();
    const {t} = useTranslation('formidable-engine');
    const [period, setPeriod] = useState<AnalyticsPeriod>('30d');
    const [reloadKey, setReloadKey] = useState(0);
    const [state, setState] = useState<LoadState>(LOADING);

    useEffect(() => {
        let cancelled = false;
        const now = new Date();
        const start = periodStart(period, now);
        // The newest first: when the period holds more than the page analyses, the newest are the ones kept.
        const submissionsQuery = buildSubmissionsQuery(formResults.path, 'created', 'descending', {startDate: start ? toDateValue(start) : undefined});

        const load = async () => {
            const submissions: AnalyticsSubmission[] = [];
            let total = 0;
            do {
                // The pages are read one after the other, as the export does.
                const {data} = await client.query({
                    query: GET_SUBMISSIONS_FOR_ANALYTICS,
                    variables: {submissionsQuery, limit: BATCH_SIZE, offset: submissions.length, workspace: 'LIVE'},
                    // Not kept in the cache: thousands of nodes read once, and the table reads the same nodes with other fields.
                    fetchPolicy: 'no-cache'
                });
                if (cancelled) {
                    return;
                }

                const result = data?.jcr?.nodesByQuery;
                const nodes = result?.nodes ?? [];
                total = result?.pageInfo?.totalCount ?? 0;
                submissions.push(...nodes.map(parseAnalyticsSubmission));
                if (nodes.length === 0) {
                    break;
                }

                setState({status: 'loading', loaded: submissions.length, total});
            } while (submissions.length < Math.min(total, MAX_SUBMISSIONS));

            setState({status: 'ready', submissions, total, now});
        };

        load().catch((error: unknown) => {
            if (!cancelled) {
                setState({status: 'error', message: error instanceof Error ? error.message : String(error)});
            }
        });

        return () => {
            cancelled = true;
        };
    }, [client, formResults.path, period, reloadKey]);

    // The page's Refresh button reads the submissions again.
    useEffect(() => {
        onRegisterRefresh(() => {
            setState(LOADING);
            setReloadKey(key => key + 1);
            return Promise.resolve();
        });
        return () => onRegisterRefresh(null);
    }, [onRegisterRefresh]);

    const fields = useMemo(() => parseAnalyticsFields(fieldLabelsData), [fieldLabelsData]);
    const analytics = useMemo(() => {
        if (state.status !== 'ready') {
            return null;
        }

        return buildAnalytics(state.submissions, fields, {
            siteKey,
            contextPath: uiContext().contextPath ?? '',
            start: periodStart(period, state.now),
            now: state.now
        });
    }, [state, fields, siteKey, period]);

    const periodOptions = ANALYTICS_PERIODS.map(value => ({value, label: t(`formResults.analytics.period.${value}`)}));
    const unknown = t('formResults.analytics.unknown');

    const handleExport = (format: 'csv' | 'json') => {
        if (!analytics || state.status !== 'ready') {
            return;
        }

        const start = periodStart(period, state.now);
        const meta: AnalyticsExportMeta = {
            form: formResultsLabel(formResults),
            site: siteKey,
            period: t(`formResults.analytics.period.${period}`),
            from: start ? toDateValue(start) : null,
            to: toDateValue(state.now),
            generatedAt: state.now.toISOString()
        };
        const filename = `form-analytics-${sanitizeFilenamePart(siteKey)}-${sanitizeFilenamePart(meta.form)}-${period}-${meta.to}.${format}`;
        if (format === 'csv') {
            downloadFile(filename, buildAnalyticsCsv(analytics, meta, t), 'text/csv;charset=utf-8;');
        } else {
            downloadFile(filename, buildAnalyticsJson(analytics, meta), 'application/json;charset=utf-8;');
        }
    };

    const toolbar = (
        <div style={{display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '8px'}}>
            <Typography variant="body" weight="bold">{t('formResults.analytics.period.label')}</Typography>
            <Dropdown
                variant="outlined"
                size="small"
                data-sel-role="analytics-period"
                data={periodOptions}
                value={period}
                onChange={(_event, item) => {
                    if (item.value && item.value !== period) {
                        setState(LOADING);
                        setPeriod(item.value as AnalyticsPeriod);
                    }
                }}
            />
            <div style={{flex: 1}}/>
            <Button
                variant="ghost"
                icon={<Download/>}
                label={t('formResults.analytics.export.csv')}
                data-sel-role="analytics-export-csv"
                isDisabled={!analytics || analytics.total === 0}
                onClick={() => handleExport('csv')}
            />
            <Button
                variant="ghost"
                icon={<Download/>}
                label={t('formResults.analytics.export.json')}
                data-sel-role="analytics-export-json"
                isDisabled={!analytics || analytics.total === 0}
                onClick={() => handleExport('json')}
            />
        </div>
    );

    const renderDistribution = (distribution: FieldDistribution) => {
        const pie = isPie(distribution);
        const items: BarItem[] = distribution.entries.map(entry => ({
            key: entry.value,
            label: distribution.kind === 'boolean' ? t(`formResults.analytics.answers.${entry.value === 'true' ? 'yes' : 'no'}`) : entry.label,
            count: entry.count,
            share: entry.share
        }));
        // A slice takes its colour from its rank among the chosen answers, in the order of the palette.
        let slice = 0;
        const colored = pie ? items.map(item => (item.count > 0 ? {...item, color: PIE_COLORS[slice++]} : item)) : items;
        const average = distribution.average === undefined ? null : distribution.average.toLocaleString(undefined, {maximumFractionDigits: 1});
        const hint = [
            t('formResults.analytics.answers.answered', {answered: formatCount(distribution.answered), total: formatCount(analytics?.total ?? 0)}),
            distribution.multiple ? t('formResults.analytics.answers.multiple') : '',
            // An amount has no breakdown: its average is the card's figure, written large below, not in this line.
            average === null || items.length === 0 ? '' : t('formResults.analytics.answers.average', {average})
        ].filter(Boolean).join(' · ');

        return (
            <Card key={distribution.name} title={distribution.label} hint={hint}>
                {items.length === 0 && average !== null && (
                    <div data-sel-role="analytics-average" style={{flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '4px', padding: '16px 0', textAlign: 'center'}}>
                        <Typography variant="title" weight="bold" style={{fontSize: '48px', lineHeight: 1.1, overflowWrap: 'anywhere'}}>{average}</Typography>
                        <Typography variant="caption" style={{color: MUTED}}>{t('formResults.analytics.answers.averageLabel')}</Typography>
                    </div>
                )}
                {items.length === 0 ? null : (
                    <div data-sel-role={pie ? 'analytics-pie' : 'analytics-bars'} style={{display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '16px'}}>
                        {pie && <PieChart items={colored} label={distribution.label}/>}
                        <div style={{flex: '1 1 180px', minWidth: 0}}>
                            <BarList items={colored} label={distribution.label}/>
                        </div>
                    </div>
                )}
            </Card>
        );
    };

    let body: React.ReactNode;
    if (state.status === 'loading' || !analytics) {
        body = (
            <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', padding: '48px'}}>
                <Loader size="big"/>
                {state.status === 'loading' && state.total > BATCH_SIZE && (
                    <Typography variant="body">
                        {t('formResults.analytics.loading', {loaded: formatCount(state.loaded), total: formatCount(Math.min(state.total, MAX_SUBMISSIONS))})}
                    </Typography>
                )}
            </div>
        );
    } else if (analytics.total === 0) {
        body = (
            <div data-sel-role="analytics-empty" style={{textAlign: 'center', padding: '48px'}}>
                <Typography variant="heading" weight="bold">{t('formResults.analytics.empty.title')}</Typography>
                <Typography variant="body">{t('formResults.analytics.empty.description')}</Typography>
            </div>
        );
    } else {
        const grid: React.CSSProperties = {display: 'grid', gap: '16px', gridTemplateColumns: 'repeat(auto-fit, minmax(min(320px, 100%), 1fr))'};
        body = (
            <>
                {state.status === 'ready' && state.total > analytics.total && (
                    <Typography variant="body" data-sel-role="analytics-capped">
                        {t('formResults.analytics.capped', {analysed: formatCount(analytics.total), total: formatCount(state.total)})}
                    </Typography>
                )}
                <div style={{display: 'grid', gap: '16px', gridTemplateColumns: 'repeat(auto-fit, minmax(min(180px, 100%), 1fr))'}}>
                    <Kpi label={t('formResults.analytics.kpi.submissions')} value={formatCount(analytics.total)}/>
                    <Kpi label={t('formResults.analytics.kpi.dailyAverage')} value={analytics.dailyAverage.toLocaleString(undefined, {maximumFractionDigits: analytics.dailyAverage < 1 ? 2 : 1})}/>
                    <Kpi label={t('formResults.analytics.kpi.pages')} value={formatCount(analytics.pages.filter(page => page.key !== null).length)}/>
                    <Kpi label={t('formResults.analytics.kpi.last')} value={analytics.lastCreated ? formatDate(analytics.lastCreated) : ''}/>
                </div>
                <Card title={t('formResults.analytics.timeline.title')} hint={t(`formResults.analytics.timeline.${analytics.timeline.unit}`)}>
                    <TimelineChart timeline={analytics.timeline} label={t('formResults.analytics.timeline.title')}/>
                </Card>
                <div style={grid}>
                    <Card title={t('formResults.analytics.pages.title')} hint={t('formResults.analytics.pages.hint')}>
                        <div data-sel-role="analytics-pages">
                            <BarList
                                label={t('formResults.analytics.pages.title')}
                                items={analytics.pages.map(page => ({key: page.key ?? '', label: page.key ?? unknown, count: page.count, share: page.share}))}
                            />
                        </div>
                    </Card>
                    <Card title={t('formResults.analytics.languages.title')}>
                        <BarList
                            label={t('formResults.analytics.languages.title')}
                            items={analytics.languages.map(language => ({key: language.key ?? '', label: language.key ?? unknown, count: language.count, share: language.share}))}
                        />
                    </Card>
                </div>
                <Card title={t('formResults.analytics.completion.title')} hint={t('formResults.analytics.completion.hint')}>
                    <div data-sel-role="analytics-completion">
                        <BarList
                            label={t('formResults.analytics.completion.title')}
                            items={analytics.completion.map(field => ({
                                key: field.name,
                                label: field.label,
                                count: field.empty,
                                share: field.emptyShare,
                                note: field.conditional
                                    ? t('formResults.analytics.completion.shownIn', {shown: formatCount(field.shown), total: formatCount(analytics.total)})
                                    : undefined
                            }))}
                        />
                    </div>
                </Card>
                {analytics.distributions.length > 0 && (
                    <>
                        <Typography variant="heading" weight="bold">{t('formResults.analytics.answers.title')}</Typography>
                        <div style={grid}>{analytics.distributions.map(renderDistribution)}</div>
                    </>
                )}
            </>
        );
    }

    return (
        <div
            data-sel-role="results-analytics"
            style={{height: '100%', overflowY: 'auto', boxSizing: 'border-box', padding: '16px', display: 'flex', flexDirection: 'column', gap: '16px', minWidth: 0, backgroundColor: 'var(--color-light)'}}
        >
            {toolbar}
            {state.status === 'error' ? <Typography color="danger">{state.message}</Typography> : body}
        </div>
    );
};
