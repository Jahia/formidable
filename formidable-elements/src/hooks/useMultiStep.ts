import {type RefObject, useCallback, useEffect, useRef, useState} from 'react';
import {applyConditionalLogicVisibility} from '~/utils/conditionalLogic';

interface UseMultiStepOptions {
	formRef: RefObject<HTMLFormElement | null>;
	stepIds?: string[];
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

export function useMultiStep({formRef, stepIds}: UseMultiStepOptions): UseMultiStepReturn {
	const [currentStep, setCurrentStep] = useState(0);
	const [visibleStepIndices, setVisibleStepIndices] = useState<number[]>([]);
	const resetVisibilityTimeoutRef = useRef<number | null>(null);

	const isMultiStep = !!(stepIds && stepIds.length > 0);
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
		setVisibleStepIndices(prev => {
			if (prev.length === indices.length && prev.every((v, j) => v === indices[j])) return prev;
			return indices;
		});
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

		return () => {
			form.removeEventListener('input', syncVisibility);
			form.removeEventListener('change', syncVisibility);
			form.removeEventListener('reset', handleReset);
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

