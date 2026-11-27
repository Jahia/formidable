/**
 * The layout of the screen, scoped to its root class, on the spacing and colour tokens of Moonstone (the legacy names,
 * which Moonstone 2.16 and 2.21 both define). The rules that override Moonstone do it for contrast or focus:
 * - a focus ring of 3:1 at least: the ring of Moonstone (accent_light) is too faint on a light page;
 * - secondary text in gray_dark, never gray (3.96:1);
 * - a pressed state filter shows a border, which forced colours keep, not only a tint;
 * - the Reject button on danger_dark: white on the danger colour of Moonstone is 3.68:1.
 * The table rules follow module-management-community: a sticky head and pager in the scrolling wrapper of the table,
 * inside the card.
 */
export const ROOT_CLASS = 'fmdbq-root';

const R = `.${ROOT_CLASS}`;
const MODAL = '.moonstone-modal';

export const QUARANTINE_CSS = `
${R} {
    isolation: isolate;
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    min-height: 0;
}
${R} .fmdbq-layout {
    flex-wrap: nowrap;
    width: 100%;
}
${R} :focus-visible,
${MODAL} :focus-visible {
    outline: 2px solid var(--color-accent_dark_contrast);
    outline-offset: 2px;
}
${R} .fmdbq-secondary {
    color: var(--color-gray_dark);
}
${R} .fmdbq-sr-only,
.fmdbq-filter-editor .fmdbq-sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    white-space: nowrap;
    border: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
}
/* The card of the list, as Form Results and module-management-community: one Paper, the toolbar on top, the table
   under it and the detail beside the table. */
${R} .fmdbq-card {
    display: flex;
    flex-direction: column;
    min-height: 0;
    padding: 0;
    overflow: hidden;
}
/* The toolbar band of the card: the strip of Form Results. */
${R} .fmdbq-toolbar {
    display: flex;
    flex: none;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--spacing-small) var(--spacing-large);
    padding: 12px 24px;
    border-bottom: 1px solid var(--color-gray_light40);
    background-color: var(--color-white);
}
${R} .fmdbq-toolbar-end {
    display: flex;
    margin-left: auto;
}
${R} .fmdbq-filter {
    display: flex;
    align-items: center;
    gap: var(--spacing-small);
}
${R} .fmdbq-card-body {
    display: flex;
    min-height: 0;
}
${R} .fmdbq-state-filter .moonstone-button[aria-pressed="true"] {
    border-bottom: 2px solid var(--color-accent_dark_contrast);
    background-color: var(--color-accent_light20);
    font-weight: 600;
}
${R} .fmdbq-chip .fmdbq-chip-empty {
    border-style: dashed;
}
.fmdbq-filter-editor {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-small);
    padding: var(--spacing-small) var(--spacing-medium);
    max-height: 320px;
    overflow-y: auto;
}
.fmdbq-filter-footer {
    display: flex;
    justify-content: flex-end;
}
${R} .fmdbq-table-wrapper tr {
    flex-wrap: nowrap;
}
/* The DataTable is a flex layout whose cells shrink: with the detail open they would clip their text. The table keeps
   the width of its columns, and the wrapper scrolls sideways instead. */
${R} .fmdbq-table-wrapper table {
    min-width: 1190px;
}
${R} .fmdbq-table-wrapper th,
${R} .fmdbq-table-wrapper td {
    flex-shrink: 0;
}
${R} .fmdbq-table-wrapper tr > :first-child {
    min-width: 340px;
}
${R} .fmdbq-pager.fmdbq-pager {
    position: sticky;
    bottom: 0;
    left: 0;
    z-index: 5;
    border-top: 1px solid var(--color-gray_light);
    background-color: var(--color-white);
}
/* one page size: the rows-per-page dropdown would offer a choice of one */
${R} .fmdbq-pager [data-testid="pagination-dropdown-items-per-page"],
${R} .fmdbq-pager [data-testid="pagination-dropdown-items-per-page"] + *,
${R} .fmdbq-pager div:has(> [data-testid="pagination-dropdown-items-per-page"]) {
    display: none;
}
${R} .fmdbq-sort-button {
    margin: 0;
    padding: 0;
    font: inherit;
    color: inherit;
    text-align: inherit;
    border: 0;
    background: none;
    cursor: pointer;
}
${R} .fmdbq-no-match {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--spacing-medium);
    padding: var(--spacing-big) 0;
}
${R} .fmdbq-list {
    display: flex;
    flex: 1 1 0;
    flex-direction: column;
    min-width: 0;
    min-height: 0;
}
${R} .fmdbq-table-wrapper {
    min-height: 0;
    max-height: calc(100vh - 230px);
    scroll-padding-top: 48px;
    scroll-padding-bottom: 64px;
    overflow: auto;
}
/* The detail panel of the card: a Paper with no card of its own, set apart from the table by a rule. */
${R} .fmdbq-detail.fmdbq-detail {
    position: relative;
    flex: 0 0 440px;
    min-width: 0;
    max-height: calc(100vh - 230px);
    overflow-y: auto;
    border-left: 1px solid var(--color-gray_light40);
    border-radius: 0;
    box-shadow: none;
}
${R} .fmdbq-detail-close {
    position: absolute;
    top: var(--spacing-small);
    right: var(--spacing-small);
}
${R} .fmdbq-detail h2 {
    padding-right: var(--spacing-big);
}
${R} .fmdbq-block {
    margin-top: var(--spacing-small);
}
${R} .fmdbq-section-title {
    margin-bottom: var(--spacing-small);
}
${R} .fmdbq-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--spacing-small);
    margin-top: var(--spacing-small);
}
${R} .fmdbq-actions .fmdbq-reject {
    margin-left: auto;
}
${R} .fmdbq-reject.moonstone-button,
${R} .fmdbq-reject.moonstone-button:hover,
${MODAL} .fmdbq-reject.moonstone-button,
${MODAL} .fmdbq-reject.moonstone-button:hover {
    background-color: var(--color-danger_dark);
    color: var(--color-white);
}
/* Cancel comes first in the DOM, as the first focus and the first Tab stop; it shows first, on the left, too */
${MODAL} .fmdbq-modal-footer {
    flex-direction: row;
    justify-content: flex-end;
    gap: var(--spacing-small);
}
${R} .fmdbq-scroll-x {
    overflow-x: auto;
}
${R} .fmdbq-detail-table tr {
    flex-wrap: nowrap;
}
/* The audit keeps the width of its columns, and its region scrolls sideways (D6) */
${R} .fmdbq-scroll-x .fmdbq-detail-table {
    min-width: 560px;
}
${R} .fmdbq-scroll-x .fmdbq-detail-table th,
${R} .fmdbq-scroll-x .fmdbq-detail-table td {
    flex: 1 0 112px;
}
${R} ul.fmdbq-inline {
    display: flex;
    flex-wrap: wrap;
    gap: var(--spacing-nano) var(--spacing-small);
    margin: 0;
    padding: 0;
    list-style: none;
}
${R} dl.fmdbq-pairs {
    display: grid;
    grid-template-columns: max-content minmax(0, 1fr);
    gap: var(--spacing-nano) var(--spacing-medium);
    margin: var(--spacing-small) 0;
}
${R} dl.fmdbq-pairs dt {
    font-weight: 600;
}
${R} dl.fmdbq-pairs dd {
    margin: 0;
    overflow-wrap: anywhere;
}
${R} code {
    font-size: 0.85em;
}
`;
