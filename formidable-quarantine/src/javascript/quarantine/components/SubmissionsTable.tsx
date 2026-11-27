import React, {useEffect, useMemo, useRef} from 'react';
import {Button, DataTable} from '@jahia/moonstone';
import type {DataTableColumn} from '@jahia/moonstone/DataTable';
import {useTranslation} from 'react-i18next';
import type {SubmissionRow} from '../graphql';
import {formatDate, PAGE_SIZE, visibleFileNames} from '../quarantine.utils';
import {pagerFallback, useDataTableA11y} from '../useDataTableA11y';
import {Reason, StateChip} from './Chips';

export const DETAIL_ID = 'fmdbq-detail';
export const LIST_HEADING_ID = 'fmdbq-list-heading';
const PAGER_CLASS = 'fmdbq-pager';

type Translate = (key: string, options?: Record<string, unknown>) => string;

/** The name of the button that opens a row: the form and the date, the same words as the heading of the detail. */
export function rowLabel(row: Pick<SubmissionRow, 'formTitle' | 'formId' | 'createdAt'>, language: string, t: Translate): string {
    return t('quarantine.table.open', {form: row.formTitle ?? row.formId, date: formatDate(row.createdAt, language)});
}

export type SortDirection = 'ascending' | 'descending';

interface SortButtonProps {
    label: string;
    direction: SortDirection;
    onSort: (direction: SortDirection) => void;
}

/**
 * The label of the Received column as a button, as the DataTable header cell answers only a mouse click. Its name
 * gives the column, the order and what a press does; the header cell carries aria-sort.
 */
const SortButton = ({label, direction, onSort}: SortButtonProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const next: SortDirection = direction === 'ascending' ? 'descending' : 'ascending';
    return (
        <button
            type="button"
            className="fmdbq-sort-button"
            aria-label={t(`quarantine.table.sort.${direction}`, {column: label})}
            data-sel-role="quarantine-sort"
            onClick={event => {
                // the header cell would toggle the sort a second time
                event.stopPropagation();
                onSort(next);
            }}
        >
            {label}
        </button>
    );
};

interface SubmissionsTableProps {
    rows: SubmissionRow[];
    totalCount: number;
    offset: number;
    sortDirection: SortDirection;
    openId: string | null;
    busy: boolean;
    language: string;
    onOpen: (id: string) => void;
    onPage: (offset: number) => void;
    onSort: (direction: SortDirection) => void;
}

/**
 * The held submissions of a page in a Moonstone DataTable, paged on the server by 25 and sorted by time of intake. A
 * row opens through the one button of its Form cell; a click on the rest of the row does the same, for the mouse
 * alone, so the row is no tab stop. The table is named by the heading of the list (LIST_HEADING_ID).
 */
export const SubmissionsTable = ({rows, totalCount, offset, sortDirection, openId, busy, language, onOpen, onPage, onSort}: SubmissionsTableProps) => {
    const {t} = useTranslation('formidable-quarantine');
    const containerRef = useRef<HTMLElement>(null);
    // the direction of the last page change, until the new page is on screen
    const pagedRef = useRef<'backward' | 'forward' | null>(null);

    useEffect(() => {
        const paged = pagedRef.current;
        if (!paged || busy) {
            return;
        }

        pagedRef.current = null;
        // a pager button disabled at an end of the list dropped the focus: it moves to the enabled side
        const active = document.activeElement as HTMLButtonElement | null;
        if (!active || active === document.body || active.disabled) {
            pagerFallback(containerRef.current, paged)?.focus();
        }
    }, [rows, busy]);

    useDataTableA11y(containerRef, {
        pagerClassName: PAGER_CLASS,
        labels: {
            first: t('quarantine.paging.first'),
            previous: t('quarantine.paging.previous'),
            next: t('quarantine.paging.next'),
            last: t('quarantine.paging.last')
        }
    });

    const columns = useMemo<DataTableColumn<SubmissionRow>[]>(() => [
        {
            key: 'formTitle',
            label: t('quarantine.table.form'),
            render: ({data}) => (
                <Button
                    variant="ghost"
                    color="accent"
                    className="fmdbq-row-button"
                    label={rowLabel(data, language, t)}
                    aria-controls={DETAIL_ID}
                    aria-expanded={data.id === openId}
                    data-sel-role="quarantine-open"
                    data-id={data.id}
                    onClick={() => onOpen(data.id)}
                />
            )
        },
        {
            key: 'createdAt',
            // the DataTable types the label as a string; it renders any node, and the button is the sort control
            label: (<SortButton label={t('quarantine.table.received')} direction={sortDirection} onSort={onSort}/>) as unknown as string,
            isSortable: true,
            width: '170px',
            render: ({value}) => formatDate(value as string, language)
        },
        {
            key: 'state',
            label: t('quarantine.table.state'),
            width: '160px',
            render: ({value}) => <StateChip state={value as string}/>
        },
        {
            key: 'attempts',
            label: t('quarantine.table.attempts'),
            width: '100px',
            align: 'right'
        },
        {
            key: 'reason',
            label: t('quarantine.table.reason'),
            width: '220px',
            render: ({value}) => <Reason code={value as string | null}/>
        },
        {
            key: 'files',
            label: t('quarantine.table.files'),
            width: '200px',
            isScrollable: true,
            render: ({data}) => {
                const files = visibleFileNames(data.files);
                return files.names.length === 0 ? t('quarantine.table.noFiles') : (
                    <ul className="fmdbq-inline">
                        {files.names.map(file => <li key={file.item}>{file.name ?? file.item}</li>)}
                        {files.more > 0 && <li>{t('quarantine.table.moreFiles', {count: files.more})}</li>}
                    </ul>
                );
            }
        }
    ], [t, language, openId, sortDirection, onOpen, onSort]);

    const currentPage = Math.floor(offset / PAGE_SIZE) + 1;

    return (
        // the scrolling area of the table inside the card of the list: the card is the Paper, this area scrolls under
        // the sticky head and pager
        <div ref={containerRef as React.RefObject<HTMLDivElement>} className="fmdbq-table-wrapper" aria-busy={busy} data-sel-role="quarantine-table">
            <DataTable
                enableSorting
                enablePagination
                primaryKey="id"
                data={rows}
                columns={columns}
                sortBy="createdAt"
                sortDirection={sortDirection}
                currentPage={currentPage}
                itemsPerPage={PAGE_SIZE}
                itemsPerPageOptions={[PAGE_SIZE]}
                totalItems={totalCount}
                aria-labelledby={LIST_HEADING_ID}
                rowProps={({data}) => ({
                    // a row does nothing on its own: its button is the tab stop, and a click on the row is a mouse shortcut
                    tabIndex: undefined,
                    isHighlighted: data.id === openId,
                    'data-sel-role': 'quarantine-row',
                    'data-id': data.id,
                    style: {cursor: 'pointer'},
                    onClick: (event: React.MouseEvent) => {
                        if (!(event.target as HTMLElement).closest('button')) {
                            onOpen(data.id);
                        }
                    }
                })}
                paginationProps={{className: PAGER_CLASS}}
                i18n={{itemsPerPage: t('quarantine.paging.itemsPerPage'), of: t('quarantine.paging.of')}}
                onSortChange={(_key, direction) => onSort(direction)}
                onPageChange={page => {
                    pagedRef.current = page < currentPage ? 'backward' : 'forward';
                    onPage((page - 1) * PAGE_SIZE);
                }}
            />
        </div>
    );
};
