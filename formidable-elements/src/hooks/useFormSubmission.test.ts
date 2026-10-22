import {describe, expect, it} from 'vitest';
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
// island checks it again before navigating, with the same forbidden characters as the server (RedirectTargetsTest).
describe('isSameOriginPath', () => {
	it('accepts a path of this server', () => {
		for (const path of ['/', '/home.html', '/sites/mysite/home.html', '/fr/compte?tab=2#top']) {
			expect(isSameOriginPath(path)).toBe(true);
		}
	});

	it('refuses everything that could leave the site or break the header it travels in', () => {
		for (const target of ['https://evil.example/', '//evil.example', String.raw`/\evil.example`, 'javascript:alert(1)', 'home.html', '', '/a b', '/a\nb', String.raw`/a\b`, '/\u0000']) {
			expect(isSameOriginPath(target), target).toBe(false);
		}
		expect(isSameOriginPath(undefined)).toBe(false);
		expect(isSameOriginPath(42)).toBe(false);
		expect(isSameOriginPath('/' + 'a'.repeat(2048))).toBe(false);
	});

	it('refuses the invisible and non-breaking characters the server refuses too', () => {
		for (const target of ['/a\u{E0001}b', '/a\uFEFFb', '/a\u200Bb', '/a\u00A0b', '/a\u2007b', '/a\u202Fb', '/a\u2028b', '/a\u0085b']) {
			expect(isSameOriginPath(target), JSON.stringify(target)).toBe(false);
		}
	});
});

describe('isHttpUrl', () => {
	it('accepts an absolute http or https URL', () => {
		for (const url of ['https://www.example.com', 'http://www.example.com/merci.html', 'HTTPS://WWW.EXAMPLE.COM/a?b=c#d', 'https://www.example.com:8443/fr/merci', 'https://b\u00FCcher.example/', 'https://my_host.example.com/']) {
			expect(isHttpUrl(url), url).toBe(true);
		}
	});

	it('refuses another scheme, a missing host, a relative target and user info', () => {
		for (const target of ['javascript:alert(1)', 'data:text/html,x', 'ftp://www.example.com/', '//evil.example/x', '/sites/mysite/home.html', 'home.html', 'https://user@evil.example/', 'https://www.example.com@evil.example/', 'https://user:pass@www.example.com/', 'https://www.example.com/a b', String.raw`https://www.example.com/\evil`, 'https://www.example.com/a\uFEFFb', '']) {
			expect(isHttpUrl(target), JSON.stringify(target)).toBe(false);
		}
		expect(isHttpUrl(undefined)).toBe(false);
		expect(isHttpUrl('https://www.example.com/' + 'a'.repeat(2048))).toBe(false);
	});
});
