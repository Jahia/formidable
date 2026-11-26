import React from 'react';
import {Button} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
import type {SubmissionRow} from '../graphql';
import {formatDate, KNOWN_REASONS, type PageRange, visibleFileNames} from '../quarantine.utils';
import {Reason, StateChip} from './Chips';

export const DETAIL_ID = 'fmdbq-detail';

interface SubmissionsTableProps {
    rows: SubmissionRow[];
    caption: string;
    openId: string | null;
    busy: boolean;
    language: string;
    /** The row buttons, by submission id, so that the screen can bring the focus back to one. */
    rowButtonsRef: React.MutableRefObject<Map<string, HTMLButtonElement>>;
    onOpen: (id: string) => void;
    noMatch?: React.ReactNode;
}

/** The name of the button that opens a row: the form and the date, the same words as the heading of the detail. */
export function rowLabel(row: Pick<SubmissionRow, 'formTitle' | 'formId' | 'createdAt'>, language: string,
                         t: (key: string, options?: Record<string, unknown>) => string): string {
    return t('quarantine.table.open', {form: row.formTitle ?? row.formId, date: formatDate(row.createdAt, language)});
}

/**
 * The held submissions of a page, oldest first. A row opens through the one button of its Form cell; a click on the
 * rest of the row does the same, for the mouse alone.
 */
export const SubmissionsTable = ({rows, caption, openId, busy, language, rowButtonsRef, onOpen, noMatch}: SubmissionsTableProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const columns = ['form', 'received', 'state', 'attempts', 'reason', 'files'] as const;

    return (
        <table className="fmdbq-table" data-sel-role="quarantine-table" aria-busy={busy}>
            <caption>{caption}</caption>
            <thead>
                <tr>
                    {columns.map(column => (
                        <th key={column} scope="col" aria-sort={column === 'received' ? 'ascending' : undefined}>
                            {t(`quarantine.table.${column}`)}
                        </th>
                    ))}
                </tr>
            </thead>
            <tbody>
                {rows.length === 0 && noMatch && (
                    <tr>
                        <td colSpan={columns.length}>{noMatch}</td>
                    </tr>
                )}
                {rows.map(row => {
                    const open = row.id === openId;
                    const files = visibleFileNames(row.files);
                    return (
                        <tr
                            key={row.id}
                            className={open ? 'fmdbq-open' : undefined}
                            data-sel-role="quarantine-row"
                            data-id={row.id}
                            onClick={event => {
                                // the mouse shortcut: the button handles its own click
                                if (!(event.target as HTMLElement).closest('button')) {
                                    onOpen(row.id);
                                }
                            }}
                        >
                            <th scope="row" style={{fontWeight: 'normal'}}>
                                <button
                                    ref={element => {
                                        if (element) {
                                            rowButtonsRef.current.set(row.id, element);
                                        } else {
                                            rowButtonsRef.current.delete(row.id);
                                        }
                                    }}
                                    type="button"
                                    className="fmdbq-row-button"
                                    aria-controls={DETAIL_ID}
                                    aria-expanded={open}
                                    data-sel-role="quarantine-open"
                                    onClick={() => onOpen(row.id)}
                                >
                                    {rowLabel(row, language, t)}
                                </button>
                            </th>
                            <td>{formatDate(row.createdAt, language)}</td>
                            <td><StateChip state={row.state}/></td>
                            <td>{row.attempts}</td>
                            <td><Reason code={row.reason} known={KNOWN_REASONS.has(row.reason ?? '')}/></td>
                            <td>
                                {files.names.length === 0 ? t('quarantine.table.noFiles') : (
                                    <ul className="fmdbq-inline">
                                        {files.names.map(file => <li key={file.item}>{file.name ?? file.item}</li>)}
                                        {files.more > 0 && <li>{t('quarantine.table.moreFiles', {count: files.more})}</li>}
                                    </ul>
                                )}
                            </td>
                        </tr>
                    );
                })}
            </tbody>
        </table>
    );
};

interface PagingProps {
    range: PageRange;
    onPage: (direction: 'previous' | 'next') => void;
}

/**
 * Previous and next page. A button at an end of the list is aria-disabled and ignores the click, so that the focus
 * stays on it; the status region of the screen announces the new range.
 */
export const Paging = ({range, onPage}: PagingProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const button = (direction: 'previous' | 'next', enabled: boolean) => (
        <Button
            label={t(`quarantine.paging.${direction}`)}
            variant="outlined"
            aria-disabled={!enabled}
            data-sel-role={`quarantine-page-${direction}`}
            onClick={() => {
                if (enabled) {
                    onPage(direction);
                }
            }}
        />
    );

    return (
        <div role="group" aria-label={t('quarantine.paging.label')} style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
            {button('previous', range.hasPrevious)}
            <span data-sel-role="quarantine-range">{t('quarantine.paging.range', {from: range.from, to: range.to, total: range.total})}</span>
            {button('next', range.hasNext)}
        </div>
    );
};
