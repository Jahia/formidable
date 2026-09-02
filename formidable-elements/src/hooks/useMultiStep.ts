import {type RefObject, useCallback, useEffect, useRef, useState} from 'react';
import {applyConditionalLogicVisibility, collectProviderRefs} from '~/utils/conditionalLogic';
import {FORM_LOGIC_INVALIDATE_EVENT, getLogicProvider} from '~/utils/logicProviders';

interface UseMultiStepOptions {
	formRef: RefObject<HTMLFormElement | null>;
	stepIds?: string[];
	/**
	 * Authoring context: the form is rendered flat — every step visible, no navigation —
	 * so the hook behaves as if the form had no steps at all.
	 */
	disabled?: boolean;
}

interface UseMultiStepReturn {
	currentStep: number;
	setCurrentStep: (step: number) => void;
	visibleStepIndices: number[];
	currentVisibleIndex: number;
	isFirstVisibleStep: boolean;
	isLastStep: boolean;
	isMultiStep: boolean;
	handleNext: (validate: () => boolean) => void;
	handlePrevious: () => void;
}

export function useMultiStep({formRef, stepIds, disabled = false}: UseMultiStepOptions): UseMultiStepReturn {
	const [currentStep, setCurrentStep] = useState(0);
	// Seeded with every step: an empty seed made indexOf(0) return -1 on the server
	// render and the first client render, so the pre-hydration markup of a multi-step
	// form carried Previous + Submit instead of Next (isLastStep on an empty list is
	// vacuously true) and mounted the captcha on step 1. The logic-hidden steps are
	// filtered out by the first computeVisibleSteps pass after mount.
	const [visibleStepIndices, setVisibleStepIndices] = useState<number[]>(
		() => (stepIds ?? []).map((unusedStepId, index) => index)
	);
	const resetVisibilityTimeoutRef = useRef<number | null>(null);

	const isMultiStep = !disabled && !!(stepIds && stepIds.length > 0);
	const currentVisibleIndex = visibleStepIndices.indexOf(currentStep);
	const isLastStep = currentVisibleIndex === visibleStepIndices.length - 1;
	const isFirstVisibleStep = currentVisibleIndex === 0;

	const stepElsRef = useRef<HTMLElement[]>([]);
	useEffect(() => {
		if (formRef.current) {
			stepElsRef.current = Array.from(formRef.current.querySelectorAll<HTMLElement>('[data-fmdb-step]'));
		}
	}, [formRef]);

	const computeVisibleSteps = useCallback(() => {
		if (!isMultiStep || !formRef.current) return;
		const indices: number[] = [];
		for (let i = 0; i < stepIds!.length; i++) {
			const wrapper = formRef.current.querySelector<HTMLElement>(`[data-fmdb-node-id="${stepIds![i]}"]`);
			if (!wrapper || wrapper.dataset.fmdbLogicHidden !== 'true') {
				indices.push(i);
			}
		}
		// eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- deliberate DOM sync: the effect reads logic-hidden markers the render cannot see
		setVisibleStepIndices(prev => {
			if (prev.length === indices.length && prev.every((v, j) => v === indices[j])) return prev;
			return indices;
		});
		// eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- deliberate DOM sync: the effect reads logic-hidden markers the render cannot see
		setCurrentStep(current => {
			if (indices.includes(current)) return current;
			return indices.find(i => i >= current) ?? indices[indices.length - 1] ?? 0;
		});
	}, [isMultiStep, stepIds, formRef]);

	useEffect(() => {
		const form = formRef.current;
		if (!form) return;

		const syncVisibility = () => {
			applyConditionalLogicVisibility(form);
			computeVisibleSteps();
		};
		const handleReset = () => {
			if (resetVisibilityTimeoutRef.current !== null) {
				window.clearTimeout(resetVisibilityTimeoutRef.current);
			}
			resetVisibilityTimeoutRef.current = window.setTimeout(() => {
				syncVisibility();
				resetVisibilityTimeoutRef.current = null;
			}, 0);
		};

		syncVisibility();

		form.addEventListener('input', syncVisibility);
		form.addEventListener('change', syncVisibility);
		form.addEventListener('reset', handleReset);

		// Any integrator can ask for a re-evaluation — after pushing to a datalayer, after a
		// consent banner is answered, after a client-side route change. This is the exact
		// mechanism; a provider's own watcher is only an approximation of it.
		//
		// Listened for on the document, not on the form: an integrator such as a consent
		// banner has no reason to know the form element, and an event dispatched on the form
		// with `bubbles: true` reaches the document anyway. The reverse would not work —
		// events do not travel downwards.
		document.addEventListener(FORM_LOGIC_INVALIDATE_EVENT, syncVisibility);

		// Sources outside the form (a JS variable such as a datalayer entry, a cookie) change
		// without any form event, so each provider that needs to is given the references it
		// must watch and reports back when their state may have moved.
		const unsubscribes = Array.from(collectProviderRefs(form), ([providerId, refs]) => {
			const provider = getLogicProvider(providerId);
			return provider?.subscribe?.(refs, syncVisibility);
		}).filter((unsubscribe): unsubscribe is () => void => typeof unsubscribe === 'function');

		return () => {
			form.removeEventListener('input', syncVisibility);
			form.removeEventListener('change', syncVisibility);
			form.removeEventListener('reset', handleReset);
			document.removeEventListener(FORM_LOGIC_INVALIDATE_EVENT, syncVisibility);
			unsubscribes.forEach(unsubscribe => unsubscribe());

			if (resetVisibilityTimeoutRef.current !== null) {
				window.clearTimeout(resetVisibilityTimeoutRef.current);
				resetVisibilityTimeoutRef.current = null;
			}
		};
	}, [formRef, computeVisibleSteps]);

	const prevStepRef = useRef(0);
	useEffect(() => {
		if (!isMultiStep) return;
		const stepEls = stepElsRef.current;
		if (stepEls[prevStepRef.current]) stepEls[prevStepRef.current].style.display = 'none';
		if (stepEls[currentStep]) stepEls[currentStep].style.display = '';
		prevStepRef.current = currentStep;
	}, [currentStep, isMultiStep]);

	useEffect(() => {
		if (formRef.current) {
			applyConditionalLogicVisibility(formRef.current);
			computeVisibleSteps();
		}
	}, [currentStep, formRef, computeVisibleSteps]);

	const handleNext = (validate: () => boolean) => {
		if (!validate()) return;
		const nextIndex = visibleStepIndices[currentVisibleIndex + 1];
		if (nextIndex !== undefined) setCurrentStep(nextIndex);
	};

	const handlePrevious = () => {
		const prevIndex = visibleStepIndices[currentVisibleIndex - 1];
		if (prevIndex !== undefined) setCurrentStep(prevIndex);
	};

	return {
		currentStep,
		setCurrentStep,
		visibleStepIndices,
		currentVisibleIndex,
		isFirstVisibleStep,
		isLastStep,
		isMultiStep,
		handleNext,
		handlePrevious,
	};
}

