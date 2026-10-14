import {describe, expect, it} from 'vitest';
import {isSameOriginPath, remainingFeedbackPause} from './useFormSubmission';

// The spinner is shown for half a second at least, measured from its appearance (#327): a fast
// answer completes the floor, an answer that took longer pays nothing more.
describe('remainingFeedbackPause', () => {
	it('completes the floor after a fast answer', () => {
		expect(remainingFeedbackPause(40)).toBe(460);
	});

	it('waits the whole floor when the answer was immediate', () => {
		expect(remainingFeedbackPause(0)).toBe(500);
	});

	it('waits nothing once the floor is reached', () => {
		expect(remainingFeedbackPause(500)).toBe(0);
	});

	it('waits nothing after a slow answer', () => {
		expect(remainingFeedbackPause(1200)).toBe(0);
	});
});

// The server only sends a path of the site in `redirect`; the island checks it again before navigating (#accounts).
describe('isSameOriginPath', () => {
	it('accepts a path of the site', () => {
		for (const path of ['/', '/home.html', '/sites/mysite/home.html', '/fr/compte?tab=2#top']) {
			expect(isSameOriginPath(path)).toBe(true);
		}
	});

	it('refuses everything that could leave the site or break the header it travels in', () => {
		for (const target of ['https://evil.example/', '//evil.example', '/\\evil.example', 'javascript:alert(1)', 'home.html', '', '/a b', '/a\nb', '/a\\b', '/\u0000']) {
			expect(isSameOriginPath(target), target).toBe(false);
		}
		expect(isSameOriginPath(undefined)).toBe(false);
		expect(isSameOriginPath(42)).toBe(false);
		expect(isSameOriginPath('/' + 'a'.repeat(2048))).toBe(false);
	});
});
