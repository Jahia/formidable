/**
 * The screens of a One-at-a-time form (docs/architecture/one-at-a-time.md): each direct child of a step —
 * or of the field list, in a form without steps — is one screen, so a fieldset is one screen with all its
 * fields. A run of content (rich text) is shown with the element that follows it, the content that ends a
 * step with the element before it.
 */

/** The marker of every element wrapper (LogicAwareRender), the unit a screen is made of. */
const WRAPPER_SELECTOR = '[data-fmdb-node-id]';

/**
 * Set by a field view on the element that holds its controls when one pick is its whole answer (a radio
 * group, a card): a pick there moves to the next screen. Part of the contract of a field type.
 */
export const ADVANCE_ON_CHANGE_ATTRIBUTE = 'data-fmdb-advance-on-change';
export const ADVANCE_ON_CHANGE_SELECTOR = `[${ADVANCE_ON_CHANGE_ATTRIBUTE}="true"]`;

/** One screen: the wrappers it shows, in document order, and the element ones among them. */
export interface Screen {
	wrappers: HTMLElement[];
	elements: HTMLElement[];
}

const isContent = (wrapper: HTMLElement): boolean => wrapper.dataset.fmdbContent === 'true';

/** The wrapper an element wrapper sits in, or null at the top of the form. */
const parentWrapper = (wrapper: HTMLElement): HTMLElement | null =>
	wrapper.parentElement?.closest<HTMLElement>(WRAPPER_SELECTOR) ?? null;

/**
 * Whether a wrapper has anything to show: not hidden by the logic, not hidden by a prefill, and, for an
 * element, not only hidden inputs — a hidden field is submitted, never seen.
 */
export const isShown = (wrapper: HTMLElement): boolean => {
	if (wrapper.dataset.fmdbLogicHidden === 'true' || wrapper.dataset.fmdbPrefilled === 'hidden') return false;
	if (isContent(wrapper)) return true;
	const onlyHiddenInputs = wrapper.querySelector('input[type="hidden"]') !== null
		&& wrapper.querySelector('input:not([type="hidden"]), select, textarea, button') === null;
	return !onlyHiddenInputs;
};

/**
 * The wrappers a screen can be made of inside `container`: a step element, or the form for a form
 * without steps. They are the children of the step's own wrapper, or the wrappers at the top of the form.
 */
export const screenWrappersOf = (container: HTMLElement): HTMLElement[] => {
	const owner = container.matches('form') ? null : container.closest<HTMLElement>(WRAPPER_SELECTOR);
	return Array.from(container.querySelectorAll<HTMLElement>(WRAPPER_SELECTOR))
		.filter(wrapper => parentWrapper(wrapper) === owner);
};

/** The shown wrappers of a container, grouped into screens. */
export const collectScreens = (container: HTMLElement): Screen[] => {
	const screens: Screen[] = [];
	let pendingContent: HTMLElement[] = [];
	for (const wrapper of screenWrappersOf(container).filter(isShown)) {
		if (isContent(wrapper)) {
			pendingContent.push(wrapper);
			continue;
		}
		screens.push({wrappers: [...pendingContent, wrapper], elements: [wrapper]});
		pendingContent = [];
	}
	if (pendingContent.length > 0) {
		const last = screens.at(-1);
		if (last) last.wrappers.push(...pendingContent);
		else screens.push({wrappers: pendingContent, elements: []});
	}
	return screens;
};

/** The index of the screen holding a node, or -1. */
export const screenIndexOf = (screens: Screen[], node: Node): number =>
	screens.findIndex(screen => screen.wrappers.some(wrapper => wrapper.contains(node)));

/**
 * Where to stand once the screens changed under the visitor (logic hid the one on screen): the screen
 * holding `anchor` if it is still shown, else the first one after it in the document, else the last.
 */
export const resolveScreenIndex = (screens: Screen[], anchor: HTMLElement | null): number => {
	if (screens.length === 0) return -1;
	if (!anchor) return 0;
	const held = screenIndexOf(screens, anchor);
	if (held !== -1) return held;
	const after = screens.findIndex(screen =>
		Boolean(anchor.compareDocumentPosition(screen.wrappers[0]) & Node.DOCUMENT_POSITION_FOLLOWING));
	return after === -1 ? screens.length - 1 : after;
};

/**
 * Whether a pick on the screen moves on by itself: the screen shows a single field, and that field's view
 * declared it with {@link ADVANCE_ON_CHANGE_ATTRIBUTE}. A fieldset is not a single field, whatever it holds.
 */
export const advancesOnPick = (screen: Screen | undefined): boolean => {
	if (screen?.elements.length !== 1) return false;
	const element = screen.elements[0];
	const zone = element.querySelector<HTMLElement>(ADVANCE_ON_CHANGE_SELECTOR);
	return zone !== null && zone.closest(WRAPPER_SELECTOR) === element;
};

/** The first control of a screen the focus can land on. */
export const firstFocusable = (screen: Screen | undefined): HTMLElement | null => {
	for (const wrapper of screen?.wrappers ?? []) {
		const control = wrapper.querySelector<HTMLElement>(
			'input:not([type="hidden"]):not(:disabled), select:not(:disabled), textarea:not(:disabled), button:not(:disabled)'
		);
		if (control) return control;
	}
	return null;
};
