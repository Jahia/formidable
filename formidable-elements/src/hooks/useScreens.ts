import {type RefObject, useCallback, useEffect, useRef, useState} from 'react';
import {
	ADVANCE_ON_CHANGE_SELECTOR,
	advancesOnPick,
	collectScreens,
	firstFocusable,
	resolveScreenIndex,
	type Screen,
	screenIndexOf,
	screenWrappersOf,
} from '~/utils/screens';
import {FORM_LOGIC_INVALIDATE_EVENT} from '~/utils/logicProviders';

/** The pause between a pick and the move, so the visitor sees what they picked before it goes. */
export const ADVANCE_DELAY_MS = 300;

/** How long after a pointer press or a Space key a click still counts as the visitor's own pick. */
const PICK_INTENT_MS = 1000;

const TEXT_LIKE_INPUT = 'input:not([type="hidden"]):not([type="radio"]):not([type="checkbox"]):not([type="button"]):not([type="submit"]):not([type="reset"])';

interface UseScreensOptions {
	formRef: RefObject<HTMLFormElement | null>;
	/** One-at-a-time, outside authoring: off, the hook does nothing and a Classic form is left as it is. */
	enabled: boolean;
	/** From useMultiStep: the step on screen and the visible ones, and the move between steps. */
	isMultiStep: boolean;
	currentStep: number;
	visibleStepIndices: number[];
	setCurrentStep: (step: number) => void;
	/** Whether the element of the screen holds — its constraints, then its field actions. */
	validate: (element: HTMLElement) => boolean | Promise<boolean>;
	/** The id of the sentence telling that a pick moves on; the fields that do point at it. */
	advanceHintId: string;
}

interface ScreensView {
	count: number;
	isFirstScreen: boolean;
	isLastScreen: boolean;
}

interface UseScreensReturn extends ScreensView {
	/** Validates the screen, then shows the next one — in the next step with a screen, past the last of a step. */
	handleNext: () => Promise<void>;
	handlePrevious: () => void;
	/** Shows the screen holding a control of the current step; false when it is already on screen. */
	showScreenOf: (control: HTMLElement) => boolean;
	/** Back to the first screen, after an accepted submission. */
	goToStart: () => void;
}

const INITIAL_VIEW: ScreensView = {count: 0, isFirstScreen: true, isLastScreen: false};

/**
 * One-at-a-time (docs/architecture/one-at-a-time.md): shows one screen of the current step at a time, and
 * moves through the steps of useMultiStep once a step has no screen left. The screens are hidden by a
 * marker (class) and a stylesheet rule (display: none), never by `style.display`, which belongs to the conditional logic.
 */
export function useScreens({
	formRef,
	enabled,
	isMultiStep,
	currentStep,
	visibleStepIndices,
	setCurrentStep,
	validate,
	advanceHintId,
}: UseScreensOptions): UseScreensReturn {
	const [view, setView] = useState<ScreensView>(INITIAL_VIEW);

	// Read by the DOM listeners and by a move that awaited its validation, so kept as refs.
	const currentStepRef = useRef(currentStep);
	const visibleStepsRef = useRef(visibleStepIndices);
	const screensRef = useRef<Screen[]>([]);
	const indexRef = useRef(-1);
	const isLastRef = useRef(false);
	/** The first wrapper of the screen on display: what keeps the visitor in place when logic changes the screens. */
	const anchorRef = useRef<HTMLElement | null>(null);
	/** Where to land once the step a move asked for is on screen. */
	const landAtRef = useRef<'first' | 'last' | null>(null);
	const focusPendingRef = useRef(false);
	const validateRef = useRef(validate);
	useEffect(() => {
		validateRef.current = validate;
	}, [validate]);

	const stepElements = useCallback((): HTMLElement[] =>
		Array.from(formRef.current?.querySelectorAll<HTMLElement>('[data-fmdb-step]') ?? []), [formRef]);

	const containerOf = useCallback((step: number): HTMLElement | null => {
		const form = formRef.current;
		if (!form) return null;
		return isMultiStep ? stepElements()[step] ?? null : form;
	}, [formRef, isMultiStep, stepElements]);

	const stepHasScreens = useCallback((step: number): boolean => {
		const container = containerOf(step);
		return container !== null && collectScreens(container).length > 0;
	}, [containerOf]);

	/** The visible step with a screen next to the current one, in a direction, or undefined. */
	const neighbourStep = useCallback((direction: 1 | -1): number | undefined => {
		if (!isMultiStep) return undefined;
		const visible = visibleStepsRef.current;
		const position = visible.indexOf(currentStepRef.current);
		const candidates = direction === 1 ? visible.slice(position + 1) : visible.slice(0, Math.max(position, 0)).reverse();
		return candidates.find(stepHasScreens);
	}, [isMultiStep, stepHasScreens]);

	const refresh = useCallback(() => {
		const form = formRef.current;
		if (!enabled || !form) return;
		const container = containerOf(currentStepRef.current);
		if (!container) return;

		const screens = collectScreens(container);
		let index: number;
		if (landAtRef.current) {
			index = landAtRef.current === 'last' ? screens.length - 1 : 0;
			landAtRef.current = null;
		} else {
			index = resolveScreenIndex(screens, anchorRef.current);
		}
		screensRef.current = screens;
		indexRef.current = index;
		anchorRef.current = screens[index]?.wrappers[0] ?? null;

		form.querySelectorAll<HTMLElement>('[data-fmdb-screen]').forEach(wrapper => {
			delete wrapper.dataset.fmdbScreen;
			delete wrapper.dataset.fmdbScreenActive;
		});
		screenWrappersOf(container).forEach(wrapper => {
			wrapper.dataset.fmdbScreen = 'true';
		});
		const screen = screens[index];
		screen?.wrappers.forEach(wrapper => {
			wrapper.dataset.fmdbScreenActive = 'true';
		});

		const isFirstScreen = index <= 0 && neighbourStep(-1) === undefined;
		const isLastScreen = index >= screens.length - 1 && neighbourStep(1) === undefined;
		isLastRef.current = isLastScreen;

		// The action key of a phone keyboard says what Enter does on this screen.
		screen?.elements.forEach(element => element.querySelectorAll(TEXT_LIKE_INPUT)
			.forEach(input => input.setAttribute('enterkeyhint', isLastScreen ? 'send' : 'next')));
		// Told before it happens (WCAG 3.2.2): the fields that move on with a pick point at the sentence saying so.
		form.querySelectorAll<HTMLElement>(ADVANCE_ON_CHANGE_SELECTOR).forEach(zone => {
			const ids = (zone.getAttribute('aria-describedby') ?? '').split(/\s+/).filter(Boolean);
			if (!ids.includes(advanceHintId)) zone.setAttribute('aria-describedby', [...ids, advanceHintId].join(' '));
		});

		form.dataset.fmdbScreensReady = 'true';

		const next: ScreensView = {count: screens.length, isFirstScreen, isLastScreen};
		// eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- deliberate DOM sync: the screens are read off the markers of the logic, which the render cannot see
		setView(previous => (previous.count === next.count && previous.isFirstScreen === next.isFirstScreen
			&& previous.isLastScreen === next.isLastScreen) ? previous : next);

		if (focusPendingRef.current) {
			focusPendingRef.current = false;
			firstFocusable(screen)?.focus();
		}
	}, [formRef, enabled, containerOf, neighbourStep, advanceHintId]);

	// A step move lands here: the step is on screen (useMultiStep's effect ran first), its screens are read.
	useEffect(() => {
		currentStepRef.current = currentStep;
		visibleStepsRef.current = visibleStepIndices;
		refresh();
	}, [currentStep, visibleStepIndices, refresh]);

	// The move a pick scheduled, until its pause is over: any move of the visitor's own cancels it.
	const advanceTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const cancelAdvance = useCallback(() => {
		if (advanceTimeoutRef.current !== null) globalThis.clearTimeout(advanceTimeoutRef.current);
		advanceTimeoutRef.current = null;
	}, []);

	const goToScreen = useCallback((index: number, focus: boolean) => {
		anchorRef.current = screensRef.current[index]?.wrappers[0] ?? null;
		focusPendingRef.current = focus;
		refresh();
	}, [refresh]);

	const goToStep = useCallback((step: number, landAt: 'first' | 'last') => {
		const sameStep = step === currentStepRef.current;
		landAtRef.current = landAt;
		focusPendingRef.current = true;
		currentStepRef.current = step;
		setCurrentStep(step);
		// Another step is read by the effect, once useMultiStep has shown it: a hidden control takes no focus.
		// The same step (the first one, current again) changes no state, so no effect runs: it is read now.
		if (sameStep) refresh();
	}, [setCurrentStep, refresh]);

	// A move in progress — its validation may wait for the field actions — ignores the next one.
	const nextInFlightRef = useRef(false);
	const handleNext = useCallback(async () => {
		if (!enabled || nextInFlightRef.current) return;
		cancelAdvance();
		nextInFlightRef.current = true;
		const fromStep = currentStepRef.current;
		const fromAnchor = anchorRef.current;
		try {
			const element = screensRef.current[indexRef.current]?.elements[0];
			if (element && !(await validateRef.current(element))) return;
			// The visitor moved meanwhile (Previous), or logic did: the move is stale.
			if (currentStepRef.current !== fromStep || anchorRef.current !== fromAnchor) return;
			// The answer may have opened or closed screens after this one.
			refresh();
			if (indexRef.current < screensRef.current.length - 1) {
				goToScreen(indexRef.current + 1, true);
				return;
			}
			const step = neighbourStep(1);
			if (step !== undefined) goToStep(step, 'first');
		} finally {
			nextInFlightRef.current = false;
		}
	}, [enabled, cancelAdvance, refresh, goToScreen, neighbourStep, goToStep]);

	const handlePrevious = useCallback(() => {
		if (!enabled) return;
		cancelAdvance();
		if (indexRef.current > 0) {
			goToScreen(indexRef.current - 1, true);
			return;
		}
		const step = neighbourStep(-1);
		if (step !== undefined) goToStep(step, 'last');
	}, [enabled, cancelAdvance, goToScreen, neighbourStep, goToStep]);

	const showScreenOf = useCallback((control: HTMLElement): boolean => {
		if (!enabled) return false;
		const index = screenIndexOf(screensRef.current, control);
		if (index === -1 || index === indexRef.current) return false;
		cancelAdvance();
		goToScreen(index, false);
		return true;
	}, [enabled, cancelAdvance, goToScreen]);

	const goToStart = useCallback(() => {
		cancelAdvance();
		anchorRef.current = null;
		if (isMultiStep) {
			goToStep(visibleStepsRef.current[0] ?? 0, 'first');
		} else {
			landAtRef.current = 'first';
			refresh();
		}
	}, [isMultiStep, cancelAdvance, goToStep, refresh]);

	const nextRef = useRef(handleNext);
	useEffect(() => {
		nextRef.current = handleNext;
	}, [handleNext]);

	// The move after a pick belongs to the screen of the pick: one the visitor or the logic left meanwhile is not
	// moved from.
	const scheduleAdvance = useCallback(() => {
		const pickedStep = currentStepRef.current;
		const pickedAnchor = anchorRef.current;
		cancelAdvance();
		advanceTimeoutRef.current = globalThis.setTimeout(() => {
			advanceTimeoutRef.current = null;
			if (currentStepRef.current === pickedStep && anchorRef.current === pickedAnchor) nextRef.current();
		}, ADVANCE_DELAY_MS);
	}, [cancelAdvance]);

	useEffect(() => {
		const form = formRef.current;
		if (!enabled || !form) return;

		// Registered after useMultiStep's own listeners: the logic has run when the screens are read again.
		const onChange = () => refresh();
		// A reset empties the form: the visitor starts again at its first screen, once the browser has
		// restored the defaults and the logic has run on them.
		let resetTimeout: ReturnType<typeof setTimeout> | null = null;
		const onReset = () => {
			if (resetTimeout !== null) globalThis.clearTimeout(resetTimeout);
			resetTimeout = globalThis.setTimeout(() => {
				resetTimeout = null;
				goToStart();
			}, 0);
		};
		// Visibility also changes with no event: a prefill hides a field it filled, a provider's watcher runs
		// the logic. The markers they write are watched; refresh writes none of them, so it does not loop.
		const observer = new MutationObserver(() => refresh());
		observer.observe(form, {subtree: true, attributes: true, attributeFilter: ['data-fmdb-logic-hidden', 'data-fmdb-prefilled']});

		// A pick moves on, an arrow key does not: arrows move the selection of a radio group, and each one
		// fires click and change. A click counts as a pick right after a pointer press or the Space key.
		let pickIntentAt = 0;
		const inAdvanceZone = (target: EventTarget | null): boolean =>
			target instanceof Element && target.closest(ADVANCE_ON_CHANGE_SELECTOR) !== null;
		const onPointerDown = (event: PointerEvent) => {
			pickIntentAt = inAdvanceZone(event.target) ? Date.now() : 0;
		};
		const onClick = (event: MouseEvent) => {
			// a click on a label is followed by the click of its input: only the input's counts
			if (!(event.target instanceof HTMLInputElement) || !inAdvanceZone(event.target)) return;
			if (Date.now() - pickIntentAt > PICK_INTENT_MS) return;
			pickIntentAt = 0;
			const screen = screensRef.current[indexRef.current];
			if (!advancesOnPick(screen) || !screen.elements[0].contains(event.target) || isLastRef.current) return;
			scheduleAdvance();
		};

		const onKeyDown = (event: KeyboardEvent) => {
			const target = event.target as HTMLElement;
			if (event.key === ' ' && inAdvanceZone(target)) {
				pickIntentAt = Date.now();
				return;
			}
			// A field that handles Enter itself (a combobox, a tag input) keeps it: its listeners on the field run
			// first, a nested island's included, its root being below the form.
			if (event.key !== 'Enter' || event.isComposing || event.defaultPrevented) return;
			if (target.closest('button, a, [contenteditable="true"]')) return;
			// Enter writes a new line in a textarea; Ctrl or Cmd with it moves on.
			if (target instanceof HTMLTextAreaElement && !(event.ctrlKey || event.metaKey)) return;
			event.preventDefault();
			if (isLastRef.current) {
				const submit = form.querySelector<HTMLButtonElement>('.fmdb-form-actions button[type="submit"]');
				if (submit && !submit.disabled) submit.click();
				return;
			}
			nextRef.current();
		};

		form.addEventListener('input', onChange);
		form.addEventListener('change', onChange);
		document.addEventListener(FORM_LOGIC_INVALIDATE_EVENT, onChange);
		form.addEventListener('reset', onReset);
		form.addEventListener('pointerdown', onPointerDown);
		form.addEventListener('click', onClick);
		form.addEventListener('keydown', onKeyDown);
		return () => {
			form.removeEventListener('input', onChange);
			form.removeEventListener('change', onChange);
			document.removeEventListener(FORM_LOGIC_INVALIDATE_EVENT, onChange);
			form.removeEventListener('reset', onReset);
			form.removeEventListener('pointerdown', onPointerDown);
			form.removeEventListener('click', onClick);
			form.removeEventListener('keydown', onKeyDown);
			observer.disconnect();
			if (resetTimeout !== null) globalThis.clearTimeout(resetTimeout);
			cancelAdvance();
		};
	}, [formRef, enabled, refresh, cancelAdvance, scheduleAdvance, goToStart]);

	return {...view, handleNext, handlePrevious, showScreenOf, goToStart};
}
