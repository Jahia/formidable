/**
 * The style of the screen, scoped to its root class. Each rule below that overrides Moonstone does it for contrast:
 * - a focus ring of 3:1 at least: the ring of Moonstone (accent_light) is too faint on a light page;
 * - secondary text in gray_dark, never gray (3.96:1);
 * - a pressed state filter shows a border, which forced colours keep, not only a tint;
 * - the Reject button on danger_dark: white on the danger colour of Moonstone is 3.68:1.
 */
export const ROOT_CLASS = 'fmdbq-root';

export const QUARANTINE_CSS = `
.${ROOT_CLASS} :focus-visible {
    outline: 2px solid var(--color-accent_dark_contrast, #004781);
    outline-offset: 2px;
}
.${ROOT_CLASS} .fmdbq-secondary {
    color: var(--color-gray_dark, #293136);
}
.${ROOT_CLASS} .fmdbq-sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
}
.${ROOT_CLASS} .fmdbq-toggle {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-height: 32px;
    padding: 4px 12px;
    border: 1px solid var(--color-gray_light, #c9cdd2);
    border-bottom-width: 2px;
    border-radius: 4px;
    background: var(--color-light, #fdfdfd);
    color: var(--color-gray_dark, #293136);
    font: inherit;
    cursor: pointer;
}
.${ROOT_CLASS} .fmdbq-toggle[aria-pressed="true"] {
    border-color: var(--color-accent_dark_contrast, #004781);
    border-bottom-width: 2px;
    background: var(--color-accent_light20, #def4fd);
    font-weight: 600;
}
.${ROOT_CLASS} .fmdbq-toggle .fmdbq-count {
    font-variant-numeric: tabular-nums;
}
.${ROOT_CLASS} .fmdbq-reject.moonstone-button,
.${ROOT_CLASS} .fmdbq-reject.moonstone-button:hover {
    background-color: var(--color-danger_dark, #840d0f);
    color: #fff;
}
.${ROOT_CLASS} table.fmdbq-table {
    width: 100%;
    border-collapse: collapse;
}
.${ROOT_CLASS} table.fmdbq-table caption {
    text-align: left;
    padding: 8px 0;
}
.${ROOT_CLASS} table.fmdbq-table th,
.${ROOT_CLASS} table.fmdbq-table td {
    text-align: left;
    vertical-align: top;
    padding: 8px;
    border-bottom: 1px solid var(--color-gray_light40, #e1e3e6);
}
.${ROOT_CLASS} table.fmdbq-table thead th {
    background: var(--color-gray_light40, #e1e3e6);
}
.${ROOT_CLASS} tr.fmdbq-open {
    background: var(--color-accent_light20, #def4fd);
}
.${ROOT_CLASS} .fmdbq-row-button {
    border: 0;
    padding: 0;
    background: none;
    color: var(--color-accent_dark_contrast, #004781);
    font: inherit;
    font-weight: 600;
    text-align: left;
    text-decoration: underline;
    cursor: pointer;
}
.${ROOT_CLASS} ul.fmdbq-inline {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 8px;
    margin: 0;
    padding: 0;
    list-style: none;
}
.${ROOT_CLASS} dl.fmdbq-pairs {
    display: grid;
    grid-template-columns: max-content minmax(0, 1fr);
    gap: 4px 12px;
    margin: 0;
}
.${ROOT_CLASS} dl.fmdbq-pairs dt {
    font-weight: 600;
}
.${ROOT_CLASS} dl.fmdbq-pairs dd {
    margin: 0;
    overflow-wrap: anywhere;
}
.${ROOT_CLASS} .fmdbq-alert {
    color: var(--color-danger_dark, #840d0f);
}
.${ROOT_CLASS} code {
    font-size: 0.85em;
}
dialog.fmdbq-dialog {
    max-width: 480px;
    border: 1px solid var(--color-gray_light, #c9cdd2);
    border-radius: 4px;
    padding: 24px;
}
dialog.fmdbq-dialog::backdrop {
    background: rgba(19, 28, 33, 0.5);
}
`;
