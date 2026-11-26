import {type RefObject, useEffect} from 'react';

const PAGER_BUTTONS: Record<string, PagerKey> = {
    'pagination-button-first-page': 'first',
    'pagination-button-previous-page': 'previous',
    'pagination-button-next-page': 'next',
    'pagination-button-last-page': 'last'
};

type PagerKey = 'first' | 'previous' | 'next' | 'last';

export interface DataTableA11yOptions {
    /** The names of the four pager buttons, which Moonstone renders as icons alone. */
    labels: Record<PagerKey, string>;
    /** The id prefix of the caption and the value of the rows-per-page dropdown. */
    rowsPerPageId: string;
    /** The class of the pager, which sticks to the bottom of the scrolling container. */
    pagerClassName: string;
}

/**
 * Patches what the Moonstone DataTable renders and takes no prop for, inside the scrolling container of the table:
 * the pager buttons get a name, the rows-per-page dropdown is named by its visible caption, the header row loses the
 * tab stop that TableRow always sets, and the scroll padding matches the sticky head and pager, so a focused control
 * never sits under them. A port of the hook of module-management-community.
 *
 * One addition: the pager disables its buttons at an end of the list, and a disabled button drops the focus, so the
 * focus that fell to the body moves to the pager button still enabled on the same side.
 *
 * React leaves an attribute it does not own in place, so the patch only has to run again after each render.
 */
export function useDataTableA11y(containerRef: RefObject<HTMLElement>, {labels, rowsPerPageId, pagerClassName}: DataTableA11yOptions): void {
    useEffect(() => {
        const container = containerRef.current;
        if (!container) {
            return;
        }

        container.querySelectorAll('thead tr[tabindex]').forEach(row => row.removeAttribute('tabindex'));

        Object.entries(PAGER_BUTTONS).forEach(([testId, key]) => {
            container.querySelector(`[data-testid="${testId}"]`)?.setAttribute('aria-label', labels[key]);
        });

        const dropdown = container.querySelector('[data-testid="pagination-dropdown-items-per-page"]');
        const listbox = dropdown?.querySelector('[role="listbox"]');
        const caption = dropdown?.previousElementSibling;
        if (listbox && caption) {
            caption.id = `${rowsPerPageId}-label`;
            listbox.id = `${rowsPerPageId}-value`;
            // the listbox keeps its own aria-label, the current value, as the second part of its name
            listbox.setAttribute('aria-labelledby', `${caption.id} ${listbox.id}`);
        }

        const head = container.querySelector<HTMLElement>('thead');
        const pager = container.querySelector<HTMLElement>(`.${pagerClassName}`);
        container.style.scrollPaddingTop = head ? `${head.offsetHeight}px` : '';
        container.style.scrollPaddingBottom = pager ? `${pager.offsetHeight}px` : '';
    });
}

/**
 * The pager button that takes the focus after a page change that disabled the focused one: Next or Last disabled at
 * the last page hand the focus to Previous, First or Previous at the first page to Next.
 */
export function pagerFallback(container: HTMLElement | null, direction: 'backward' | 'forward'): HTMLElement | null {
    if (!container) {
        return null;
    }

    const testId = direction === 'forward' ? 'pagination-button-previous-page' : 'pagination-button-next-page';
    const button = container.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
    return button && !button.disabled ? button : null;
}
