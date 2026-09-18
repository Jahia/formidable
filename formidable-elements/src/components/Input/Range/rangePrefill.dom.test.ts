// @vitest-environment jsdom
import {describe, expect, it} from 'vitest';
import {applyAfterPrefill} from './rangePrefill';

/**
 * What the island does with the author's choice after a prefill it accepted, on the markup the view
 * produces: the named mirror inside the element wrapper. The script leaves this shape to the island
 * because the mirror says nothing of the slider's verdict, so the island's own hand is what is tested.
 */
const slider = () => {
	document.body.innerHTML = '<div data-fmdb-node-name="satisfaction"><input type="range"><input type="hidden" name="satisfaction" value="7"></div>';
	return {
		wrapper: document.querySelector<HTMLElement>('[data-fmdb-node-name]')!,
		mirror: document.querySelector<HTMLElement>('input[type="hidden"]')!
	};
};

describe('applyAfterPrefill', () => {
	it('read-only: marks the wrapper and asks for the slider to be locked, the mirror untouched', () => {
		const {wrapper, mirror} = slider();
		expect(applyAfterPrefill('readOnly', mirror)).toBe(true);
		expect(wrapper.getAttribute('data-fmdb-prefilled')).toBe('readonly');
		expect(wrapper.style.display).toBe('');
		expect((mirror as HTMLInputElement).value).toBe('7');
	});

	it('hidden: takes the wrapper out of sight, the mirror still there to be submitted', () => {
		const {wrapper, mirror} = slider();
		expect(applyAfterPrefill('hidden', mirror)).toBe(false);
		expect(wrapper.getAttribute('data-fmdb-prefilled')).toBe('hidden');
		expect(wrapper.style.display).toBe('none');
		expect(wrapper.getAttribute('aria-hidden')).toBe('true');
		expect((mirror as HTMLInputElement).disabled).toBe(false);
	});

	it('editable, or nothing asked: changes nothing', () => {
		const {wrapper, mirror} = slider();
		expect(applyAfterPrefill(undefined, mirror)).toBe(false);
		expect(applyAfterPrefill('editable', mirror)).toBe(false);
		expect(wrapper.hasAttribute('data-fmdb-prefilled')).toBe(false);
	});

	it('survives a mirror outside any element wrapper', () => {
		document.body.innerHTML = '<input type="hidden" name="loose">';
		const mirror = document.querySelector<HTMLElement>('input')!;
		expect(applyAfterPrefill('hidden', mirror)).toBe(false);
		expect(applyAfterPrefill('readOnly', null)).toBe(true);
	});
});
