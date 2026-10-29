import {describe, expect, it} from 'vitest';
import sharedCases from '../../../formidable-engine/src/test/resources/redirect-targets.json';
import {isHttpUrl, isSameOriginPath, remainingFeedbackPause} from './useFormSubmission';

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

// The server sends a path of this server, or an absolute http(s) URL read from the configuration, in `redirect`; the
// island checks it again before navigating. One list of cases, read by RedirectTargetsTest too, keeps the two checks
// together: the island accepts a target, and what the server sends for it, exactly when the server sends something.
interface SharedCase {
	target: string;
	sameOriginPath: string | null;
	httpUrl: string | null;
}
const {cases} = sharedCases as {cases: SharedCase[]};

describe('isSameOriginPath', () => {
	it.each(cases)('agrees with the server on $target', ({target, sameOriginPath}) => {
		expect(isSameOriginPath(target)).toBe(sameOriginPath !== null);
		if (sameOriginPath !== null) expect(isSameOriginPath(sameOriginPath)).toBe(true);
	});

	it('refuses what is not a string, and a target over 2048 characters', () => {
		expect(isSameOriginPath(undefined)).toBe(false);
		expect(isSameOriginPath(42)).toBe(false);
		expect(isSameOriginPath('/' + 'a'.repeat(2048))).toBe(false);
	});
});

describe('isHttpUrl', () => {
	it.each(cases)('agrees with the server on $target', ({target, httpUrl}) => {
		expect(isHttpUrl(target)).toBe(httpUrl !== null);
		if (httpUrl !== null) expect(isHttpUrl(httpUrl)).toBe(true);
	});

	it('refuses what is not a string, and a target over 2048 characters', () => {
		expect(isHttpUrl(undefined)).toBe(false);
		expect(isHttpUrl('https://www.example.com/' + 'a'.repeat(2048))).toBe(false);
	});
});
