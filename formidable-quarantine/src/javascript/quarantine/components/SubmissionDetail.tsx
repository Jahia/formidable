import React, {useId} from 'react';
import {Banner, Button, Close, DataTable, Paper, Reload, Separator, Typography} from '@jahia/moonstone';
import type {DataTableColumn} from '@jahia/moonstone/DataTable';
import {useTranslation} from 'react-i18next';
import type {HeldDetail, HeldFile} from '../graphql';
import {
    buttonsFor,
    formatDate,
    formatSize,
    KNOWN_REASONS,
    type QuarantineOperation,
    stateOfStored
} from '../quarantine.utils';
import {Reason, StateChip, VerdictChip} from './Chips';
import {DETAIL_ID} from './SubmissionsTable';

interface ActionButtonsProps {
    allowedOperations: readonly string[];
    /** The operation that runs, whose button says so; null when none does. */
    running: QuarantineOperation | null;
    onOperation: (operation: QuarantineOperation) => void;
}

/**
 * The buttons of the operations that the API allows, and no other, in the fixed order: Reject last, set apart. Not a
 * ButtonGroup: it gives every button one colour, and Reject has its own.
 */
export const ActionButtons = ({allowedOperations, running, onOperation}: ActionButtonsProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const operations = buttonsFor(allowedOperations);
    if (operations.length === 0) {
        return (
            <Typography variant="body" className="fmdbq-secondary" data-sel-role="quarantine-no-actions">
                {t('quarantine.detail.noActions')}
            </Typography>
        );
    }

    return (
        <div role="group" aria-label={t('quarantine.detail.actions')} className="fmdbq-actions">
            {operations.map(operation => {
                const reject = operation === 'REJECT';
                const busy = running === operation;
                return (
                    <Button
                        key={operation}
                        label={busy ? t(`quarantine.action.busy.${operation}`) : t(`quarantine.action.${operation}`)}
                        color={reject ? 'danger' : 'default'}
                        variant={reject ? 'default' : 'outlined'}
                        className={reject ? 'fmdbq-reject' : undefined}
                        aria-disabled={running !== null}
                        data-sel-role={`quarantine-action-${operation.toLowerCase()}`}
                        onClick={() => {
                            if (running === null) {
                                onOperation(operation);
                            }
                        }}
                    />
                );
            })}
        </div>
    );
};

type CheckRow = HeldFile['checks'][number];

const FileChecks = ({file}: {file: HeldFile}) => {
    const {t} = useTranslation('formidable-quarantine');
    const titleId = useId();
    const name = file.name ?? file.item;
    const columns: DataTableColumn<CheckRow>[] = [
        {key: 'name', label: t('quarantine.detail.check'), width: '34%'},
        {key: 'verdict', label: t('quarantine.detail.verdict'), width: '33%', render: ({value}) => <VerdictChip verdict={value as string}/>},
        {key: 'reasonCode', label: t('quarantine.detail.reason'), render: ({value}) => (value ? <code>{value as string}</code> : '')}
    ];
    return (
        <div className="fmdbq-block" data-sel-role="quarantine-file">
            <Typography id={titleId} component="h4" variant="body" weight="bold">{name}</Typography>
            <dl className="fmdbq-pairs">
                <dt>{t('quarantine.detail.fileType')}</dt>
                <dd>{file.mimeType ?? ''}</dd>
                <dt>{t('quarantine.detail.fileSize')}</dt>
                <dd>{formatSize(file.size)}</dd>
            </dl>
            {!file.bytesPresent && <Typography className="fmdbq-secondary">{t('quarantine.detail.bytesDeleted')}</Typography>}
            {file.checks.length === 0 ? <Typography className="fmdbq-secondary">{t('quarantine.detail.noChecks')}</Typography> : (
                <DataTable
                    enableSorting={false}
                    enablePagination={false}
                    primaryKey="name"
                    data={file.checks}
                    columns={columns}
                    aria-labelledby={titleId}
                    rowProps={{tabIndex: undefined}}
                    className="fmdbq-detail-table"
                />
            )}
        </div>
    );
};

/** A stored state of the audit for a reader: its label when the screen knows it. */
const StoredState = ({stored}: {stored: string}) => {
    const {t} = useTranslation('formidable-quarantine');
    const state = stateOfStored(stored);
    return <>{state ? t(`quarantine.state.${state}`) : stored}</>;
};

const SectionTitle = ({id, children}: {id?: string; children: React.ReactNode}) => (
    <Typography id={id} component="h3" variant="subheading" weight="bold" className="fmdbq-section-title">{children}</Typography>
);

interface AuditRow {
    key: string;
    operation: string;
    user: string | null;
    from: string;
    to: string;
    at: string;
}

const Audit = ({submission, language, titleId}: {submission: HeldDetail; language: string; titleId: string}) => {
    const {t} = useTranslation('formidable-quarantine');
    // the audit has no identifier: its order is its key
    const rows: AuditRow[] = submission.audit.map((record, index) => ({key: String(index), ...record}));
    const columns: DataTableColumn<AuditRow>[] = [
        {key: 'operation', label: t('quarantine.detail.audit_operation'), render: ({value}) => t(`quarantine.audit.${value as string}`, {defaultValue: value as string})},
        {key: 'user', label: t('quarantine.detail.audit_user'), render: ({value}) => (value as string | null) ?? t('quarantine.audit.system')},
        {key: 'from', label: t('quarantine.detail.audit_from'), render: ({value}) => <StoredState stored={value as string}/>},
        {key: 'to', label: t('quarantine.detail.audit_to'), render: ({value}) => <StoredState stored={value as string}/>},
        {key: 'at', label: t('quarantine.detail.audit_at'), render: ({value}) => <time dateTime={value as string}>{formatDate(value as string, language)}</time>}
    ];
    return (
        <div tabIndex={0} role="region" aria-label={t('quarantine.detail.auditRegion')} className="fmdbq-scroll-x" data-sel-role="quarantine-audit">
            <DataTable
                enableSorting={false}
                enablePagination={false}
                primaryKey="key"
                data={rows}
                columns={columns}
                aria-labelledby={titleId}
                rowProps={{tabIndex: undefined}}
                className="fmdbq-detail-table"
            />
        </div>
    );
};

const Decision = ({submission, language}: {submission: HeldDetail; language: string}) => {
    const {t} = useTranslation('formidable-quarantine');
    const decision = submission.decision;
    if (!decision) {
        return <Typography className="fmdbq-secondary">{t('quarantine.detail.noDecision')}</Typography>;
    }

    const fileName = submission.files.find(file => file.item === decision.item)?.name ?? decision.item ?? '';
    return (
        <dl className="fmdbq-pairs" data-sel-role="quarantine-decision">
            <dt>{t('quarantine.detail.reason')}</dt>
            <dd><Reason code={decision.reason} known={KNOWN_REASONS.has(decision.reason)}/></dd>
            {decision.item && (
                <>
                    <dt>{t('quarantine.detail.decisionFile')}</dt>
                    <dd>{fileName}</dd>
                </>
            )}
            {decision.check && (
                <>
                    <dt>{t('quarantine.detail.check')}</dt>
                    <dd>{decision.check}</dd>
                </>
            )}
            {decision.at && (
                <>
                    <dt>{t('quarantine.detail.at')}</dt>
                    <dd>{formatDate(decision.at, language)}</dd>
                </>
            )}
        </dl>
    );
};

interface SubmissionDetailProps {
    submission: HeldDetail;
    language: string;
    headingRef: React.RefObject<HTMLHeadingElement>;
    actions: React.ReactNode;
    /** Whether the last operation found the submission changed: the message and its Reload button show. */
    changed: boolean;
    reloading: boolean;
    onReload: () => void;
}

/** The body of the detail: the heading, the state, the changed message, the actions, then the records. */
export const SubmissionDetail = ({submission, language, headingRef, actions, changed, reloading, onReload}: SubmissionDetailProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const auditTitleId = useId();
    const form = submission.formTitle ?? submission.formId;

    return (
        <>
            <Typography ref={headingRef} component="h2" variant="heading" weight="bold" tabIndex={-1} id={`${DETAIL_ID}-title`}>
                {t('quarantine.detail.title', {form, date: formatDate(submission.createdAt, language)})}
            </Typography>
            <div className="fmdbq-block"><StateChip state={submission.state}/></div>
            {/* a live region, always mounted, so that the screen reader reads the message when it appears */}
            <div role="alert" data-sel-role="quarantine-changed">
                {changed && (
                    <Banner role="note" variant="danger" title={t('quarantine.changedTitle')} className="fmdbq-block">
                        <Typography>{t('quarantine.changed')}</Typography>
                        <Button
                            label={t('quarantine.reload')}
                            icon={<Reload aria-hidden="true"/>}
                            variant="outlined"
                            isLoading={reloading}
                            className="fmdbq-block"
                            data-sel-role="quarantine-reload"
                            onClick={onReload}
                        />
                    </Banner>
                )}
            </div>
            {actions}
            <Separator spacing="medium"/>
            <div tabIndex={0} role="region" aria-label={t('quarantine.detail.region')}>
                <SectionTitle>{t('quarantine.detail.files')}</SectionTitle>
                {submission.files.length === 0 && <Typography className="fmdbq-secondary">{t('quarantine.detail.noFiles')}</Typography>}
                {submission.files.map(file => <FileChecks key={file.item} file={file}/>)}

                <Separator spacing="medium"/>
                <SectionTitle>{t('quarantine.detail.decision')}</SectionTitle>
                <Decision submission={submission} language={language}/>

                <Separator spacing="medium"/>
                <SectionTitle id={auditTitleId}>{t('quarantine.detail.audit')}</SectionTitle>
                <Audit submission={submission} language={language} titleId={auditTitleId}/>

                {submission.publicValues && (
                    <>
                        <Separator spacing="medium"/>
                        <SectionTitle>{t('quarantine.detail.values')}</SectionTitle>
                        {submission.publicValues.length === 0 ? <Typography className="fmdbq-secondary">{t('quarantine.detail.valuesDeleted')}</Typography> : (
                            <dl className="fmdbq-pairs" data-sel-role="quarantine-values">
                                {submission.publicValues.map(value => (
                                    <React.Fragment key={value.name}>
                                        <dt>{value.name}</dt>
                                        <dd>{value.values.join(', ')}</dd>
                                    </React.Fragment>
                                ))}
                            </dl>
                        )}
                    </>
                )}

                {submission.sensitiveFields.length > 0 && (
                    <>
                        <Separator spacing="medium"/>
                        <SectionTitle>{t('quarantine.detail.sensitive')}</SectionTitle>
                        <Typography className="fmdbq-secondary">{t('quarantine.detail.sensitiveNote')}</Typography>
                        <ul data-sel-role="quarantine-sensitive">
                            {submission.sensitiveFields.map(name => <li key={name}>{name}</li>)}
                        </ul>
                    </>
                )}
            </div>
        </>
    );
};

interface DetailFrameProps {
    onClose: () => void;
    children: React.ReactNode;
}

/**
 * The frame of the detail: a Moonstone Paper, which renders a section, named by the heading of the detail, closed by
 * its button or by Escape from inside it. It sits in the card of the list, beside the table, as the detail panel of
 * Form Results does. It is not modal: the focus moves freely between the list and the detail.
 */
export const DetailFrame = ({onClose, children}: DetailFrameProps) => {
    const {t} = useTranslation('formidable-quarantine');
    return (
        <Paper
            id={DETAIL_ID}
            aria-labelledby={`${DETAIL_ID}-title`}
            data-sel-role="quarantine-detail"
            className="fmdbq-detail"
            onKeyDown={event => {
                // Escape inside the detail closes it
                if (event.key === 'Escape' && !event.defaultPrevented) {
                    onClose();
                }
            }}
        >
            <div className="fmdbq-detail-close">
                <Button
                    variant="ghost"
                    icon={<Close aria-hidden="true"/>}
                    aria-label={t('quarantine.detail.close')}
                    data-sel-role="quarantine-detail-close"
                    onClick={onClose}
                />
            </div>
            {children}
        </Paper>
    );
};
