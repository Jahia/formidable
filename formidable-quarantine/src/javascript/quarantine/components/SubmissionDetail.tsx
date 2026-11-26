import React from 'react';
import {Button, Close, Reload, Typography, Warning} from '@jahia/moonstone';
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
    actionButtonsRef: React.MutableRefObject<Map<QuarantineOperation, HTMLButtonElement>>;
    onOperation: (operation: QuarantineOperation) => void;
}

/** The buttons of the operations that the API allows, and no other, in the fixed order: Reject last, set apart. */
export const ActionButtons = ({allowedOperations, running, actionButtonsRef, onOperation}: ActionButtonsProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const operations = buttonsFor(allowedOperations);
    if (operations.length === 0) {
        return <p className="fmdbq-secondary" data-sel-role="quarantine-no-actions">{t('quarantine.detail.noActions')}</p>;
    }

    return (
        <div role="group" aria-label={t('quarantine.detail.actions')} style={{display: 'flex', gap: '8px', flexWrap: 'wrap'}}>
            {operations.map(operation => {
                const reject = operation === 'REJECT';
                const busy = running === operation;
                return (
                    <Button
                        key={operation}
                        ref={(element: HTMLButtonElement | null) => {
                            if (element) {
                                actionButtonsRef.current.set(operation, element);
                            } else {
                                actionButtonsRef.current.delete(operation);
                            }
                        }}
                        label={busy ? t(`quarantine.action.busy.${operation}`) : t(`quarantine.action.${operation}`)}
                        color={reject ? 'danger' : 'default'}
                        variant={reject ? 'default' : 'outlined'}
                        className={reject ? 'fmdbq-reject' : undefined}
                        style={reject ? {marginLeft: 'auto'} : undefined}
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

const FileChecks = ({file}: {file: HeldFile}) => {
    const {t} = useTranslation('formidable-quarantine');
    const name = file.name ?? file.item;
    return (
        <div style={{marginBottom: '16px'}} data-sel-role="quarantine-file">
            <Typography component="h4" variant="body" weight="bold">{name}</Typography>
            <dl className="fmdbq-pairs">
                <dt>{t('quarantine.detail.fileType')}</dt>
                <dd>{file.mimeType ?? ''}</dd>
                <dt>{t('quarantine.detail.fileSize')}</dt>
                <dd>{formatSize(file.size)}</dd>
            </dl>
            {!file.bytesPresent && <p className="fmdbq-secondary">{t('quarantine.detail.bytesDeleted')}</p>}
            {file.checks.length === 0 ? <p className="fmdbq-secondary">{t('quarantine.detail.noChecks')}</p> : (
                <table className="fmdbq-table">
                    <caption>{t('quarantine.detail.checksCaption', {name})}</caption>
                    <thead>
                        <tr>
                            <th scope="col">{t('quarantine.detail.check')}</th>
                            <th scope="col">{t('quarantine.detail.verdict')}</th>
                            <th scope="col">{t('quarantine.detail.reason')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {file.checks.map(check => (
                            <tr key={check.name}>
                                <th scope="row" style={{fontWeight: 'normal'}}>{check.name}</th>
                                <td><VerdictChip verdict={check.verdict}/></td>
                                <td>{check.reasonCode ? <code>{check.reasonCode}</code> : ''}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
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

interface SubmissionDetailProps {
    submission: HeldDetail;
    language: string;
    headingRef: React.RefObject<HTMLHeadingElement>;
    actions: React.ReactNode;
    /** Whether the last operation found the submission changed: the message and its Reload button show. */
    changed: boolean;
    reloading: boolean;
    reloadRef: React.RefObject<HTMLButtonElement>;
    onReload: () => void;
}

/** The body of the detail: the heading, the state, the changed message, the actions, then the records. */
export const SubmissionDetail = ({submission, language, headingRef, actions, changed, reloading, reloadRef, onReload}: SubmissionDetailProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const form = submission.formTitle ?? submission.formId;
    const fileName = (item: string | null) => submission.files.find(file => file.item === item)?.name ?? item ?? '';

    return (
        <>
            <Typography ref={headingRef} component="h2" variant="heading" weight="bold" tabIndex={-1} id={`${DETAIL_ID}-title`}>
                {t('quarantine.detail.title', {form, date: formatDate(submission.createdAt, language)})}
            </Typography>
            <div style={{margin: '8px 0'}}><StateChip state={submission.state}/></div>
            {/* always mounted, so that the screen reader reads the message when it appears */}
            <div role="alert" data-sel-role="quarantine-changed">
                {changed && (
                    <div className="fmdbq-alert" style={{display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap', margin: '8px 0'}}>
                        <Warning aria-hidden="true"/>
                        <span>{t('quarantine.changed')}</span>
                        <Button
                            ref={reloadRef}
                            label={t('quarantine.reload')}
                            icon={<Reload aria-hidden="true"/>}
                            variant="outlined"
                            isLoading={reloading}
                            data-sel-role="quarantine-reload"
                            onClick={onReload}
                        />
                    </div>
                )}
            </div>
            {actions}
            <div tabIndex={0} role="region" aria-label={t('quarantine.detail.region')} style={{marginTop: '16px'}}>
                <Typography component="h3" variant="subheading" weight="bold">{t('quarantine.detail.files')}</Typography>
                {submission.files.length === 0 && <p className="fmdbq-secondary">{t('quarantine.detail.noFiles')}</p>}
                {submission.files.map(file => <FileChecks key={file.item} file={file}/>)}

                <Typography component="h3" variant="subheading" weight="bold">{t('quarantine.detail.decision')}</Typography>
                {submission.decision ? (
                    <dl className="fmdbq-pairs" data-sel-role="quarantine-decision">
                        <dt>{t('quarantine.detail.reason')}</dt>
                        <dd><Reason code={submission.decision.reason} known={KNOWN_REASONS.has(submission.decision.reason)}/></dd>
                        {submission.decision.item && (
                            <>
                                <dt>{t('quarantine.detail.decisionFile')}</dt>
                                <dd>{fileName(submission.decision.item)}</dd>
                            </>
                        )}
                        {submission.decision.check && (
                            <>
                                <dt>{t('quarantine.detail.check')}</dt>
                                <dd>{submission.decision.check}</dd>
                            </>
                        )}
                        {submission.decision.at && (
                            <>
                                <dt>{t('quarantine.detail.at')}</dt>
                                <dd>{formatDate(submission.decision.at, language)}</dd>
                            </>
                        )}
                    </dl>
                ) : <p className="fmdbq-secondary">{t('quarantine.detail.noDecision')}</p>}

                <Typography component="h3" variant="subheading" weight="bold">{t('quarantine.detail.audit')}</Typography>
                <div tabIndex={0} role="region" aria-label={t('quarantine.detail.auditRegion')} style={{overflowX: 'auto'}}>
                    <table className="fmdbq-table" data-sel-role="quarantine-audit">
                        <caption className="fmdbq-sr-only">{t('quarantine.detail.auditCaption')}</caption>
                        <thead>
                            <tr>
                                {(['operation', 'user', 'from', 'to', 'at'] as const).map(column => (
                                    <th key={column} scope="col" aria-sort={column === 'at' ? 'ascending' : undefined}>
                                        {t(`quarantine.detail.audit_${column}`)}
                                    </th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {submission.audit.map((record, index) => (
                                // the audit has no identifier: its order is its key
                                // eslint-disable-next-line @eslint-react/no-array-index-key
                                <tr key={index}>
                                    <td>{t(`quarantine.audit.${record.operation}`, {defaultValue: record.operation})}</td>
                                    <td>{record.user ?? t('quarantine.audit.system')}</td>
                                    <td><StoredState stored={record.from}/></td>
                                    <td><StoredState stored={record.to}/></td>
                                    <td><time dateTime={record.at}>{formatDate(record.at, language)}</time></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                {submission.publicValues && (
                    <>
                        <Typography component="h3" variant="subheading" weight="bold">{t('quarantine.detail.values')}</Typography>
                        {submission.publicValues.length === 0 ? <p className="fmdbq-secondary">{t('quarantine.detail.valuesDeleted')}</p> : (
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
                        <Typography component="h3" variant="subheading" weight="bold">{t('quarantine.detail.sensitive')}</Typography>
                        <p className="fmdbq-secondary">{t('quarantine.detail.sensitiveNote')}</p>
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
 * The frame of the detail: a section named by its heading, closed by its button or by Escape from inside it. It is
 * not modal: the focus moves freely between the list and the detail.
 */
export const DetailFrame = ({onClose, children}: DetailFrameProps) => {
    const {t} = useTranslation('formidable-quarantine');
    return (
        <section
            id={DETAIL_ID}
            aria-labelledby={`${DETAIL_ID}-title`}
            data-sel-role="quarantine-detail"
            style={{position: 'relative', flex: '0 0 480px', minWidth: 0, overflowY: 'auto', padding: '16px', backgroundColor: 'var(--color-light)'}}
            onKeyDown={event => {
                // Escape inside the detail closes it, unless a dialog of the detail handles it first
                if (event.key === 'Escape' && !event.defaultPrevented) {
                    onClose();
                }
            }}
        >
            <div style={{position: 'absolute', top: '12px', right: '12px'}}>
                <Button
                    variant="ghost"
                    icon={<Close aria-hidden="true"/>}
                    aria-label={t('quarantine.detail.close')}
                    data-sel-role="quarantine-detail-close"
                    onClick={onClose}
                />
            </div>
            <div style={{paddingRight: '40px'}}>{children}</div>
        </section>
    );
};
