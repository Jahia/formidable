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

/**
 * Set by a field view on any of its elements when the field shows nothing, such as a capture that only
 * posts hidden inputs: its wrapper is not a screen. Part of the contract of a field type.
 */
export const NO_SCREEN_ATTRIBUTE = 'data-fmdb-no-screen';
const NO_SCREEN_SELECTOR = `[${NO_SCREEN_ATTRIBUTE}="true"]`;

/** One screen: the wrappers it shows, in document order, and the element ones among them. */
export interface Screen {
	wrappers: HTMLElement[];
	elements: HTMLElement[];
}

const isContent = (wrapper: HTMLElement): boolean => wrapper.dataset.fmdbContent === 'true';

/** The wrapper an element wrapper sits in, or null at the top of the form. */
const parentWrapper = (wrapper: HTMLElement): HTMLElement | null =>
	wrapper.parentElement?.closest<HTMLElement>(WRAPPER_SELECTOR) ?? null;

/** The node type of a hidden field: submitted, never seen. Its wrapper names it (`data-fmdb-node-type`). */
const HIDDEN_FIELD_TYPE = 'fmdb:inputHidden';

/** The wrappers directly inside a wrapper: the fields of a fieldset. */
const childWrappersOf = (wrapper: HTMLElement): HTMLElement[] =>
	Array.from(wrapper.querySelectorAll<HTMLElement>(WRAPPER_SELECTOR)).filter(child => parentWrapper(child) === wrapper);

/**
 * Whether a wrapper has anything to show: not hidden by the logic, not hidden by a prefill, not a hidden
 * field, not a field whose view declared it shows nothing, and, for a container (a fieldset), one of its
 * own wrappers shown. A field of another type shows, whatever controls it is made of.
 */
export const isShown = (wrapper: HTMLElement): boolean => {
	if (wrapper.dataset.fmdbLogicHidden === 'true' || wrapper.dataset.fmdbPrefilled === 'hidden') return false;
	if (isContent(wrapper)) return true;
	if (wrapper.dataset.fmdbNodeType === HIDDEN_FIELD_TYPE) return false;
	const children = childWrappersOf(wrapper);
	if (children.length === 0) return wrapper.querySelector(NO_SCREEN_SELECTOR) === null;
	return children.some(isShown);
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
 * The zone where a pick on the screen moves on by itself, or null: the screen shows a single field, and that
 * field's view declared the zone with {@link ADVANCE_ON_CHANGE_ATTRIBUTE}. A fieldset is not a single field,
 * whatever it holds.
 */
export const advanceZoneOf = (screen: Screen | undefined): HTMLElement | null => {
	if (screen?.elements.length !== 1) return null;
	const element = screen.elements[0];
	const zone = element.querySelector<HTMLElement>(ADVANCE_ON_CHANGE_SELECTOR);
	return zone !== null && zone.closest(WRAPPER_SELECTOR) === element ? zone : null;
};

/** Whether a pick on the screen moves on by itself: see {@link advanceZoneOf}. */
export const advancesOnPick = (screen: Screen | undefined): boolean => advanceZoneOf(screen) !== null;

/** Whether two lists of screens show the same wrappers, in the same order. */
export const sameScreens = (a: Screen[], b: Screen[]): boolean =>
	a.length === b.length && a.every((screen, i) => screen.wrappers.length === b[i].wrappers.length
		&& screen.wrappers.every((wrapper, j) => wrapper === b[i].wrappers[j]));

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
