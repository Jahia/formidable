import {describe, expect, it} from 'vitest';
import type {FileTypes} from '~/utils/fileTypes.server';
import {accepts, buildAcceptAttr, getDisplayFormats, isRestricted, matchesAcceptToken} from './fileAccept';

/** What the engine hands the island for a field accepting PDF, QuickTime and every allowed image (only PNG). */
const types: FileTypes = {
	tokens: ['application/pdf', 'video/quicktime', 'image/*'],
	shown: {'application/pdf': ['.pdf'], 'video/quicktime': ['.qt'], 'image/*': ['.png']},
	recognised: {'application/pdf': ['.pdf'], 'video/quicktime': ['.qt', '.mov'], 'image/*': ['.png']}
};

const file = (name: string, type = '') => ({name, type});

describe('matchesAcceptToken', () => {
	it('takes the type the browser gives, a wildcard by its top-level type', () => {
		expect(matchesAcceptToken(file('cv.bin', 'application/pdf'), 'application/pdf', types)).toBe(true);
		expect(matchesAcceptToken(file('photo', 'IMAGE/PNG'), 'image/*', types)).toBe(true);
		expect(matchesAcceptToken(file('photo', 'video/mp4'), 'image/*', types)).toBe(false);
	});

	it('falls back on every extension Tika recognises when the browser gives no type', () => {
		expect(matchesAcceptToken(file('Clip.MOV'), 'video/quicktime', types)).toBe(true);
		expect(matchesAcceptToken(file('clip.mp4'), 'video/quicktime', types)).toBe(false);
	});

	it('lets a file through for a type Tika knows no extension of: the server checks its real type', () => {
		expect(matchesAcceptToken(file('anything.xyz'), 'application/x-custom', types)).toBe(true);
	});
});

describe('accepts', () => {
	it('restricts nothing without the engine answer, and refuses every file when no type is left', () => {
		const unknown: FileTypes = {shown: {}, recognised: {}};
		const none: FileTypes = {tokens: [], shown: {}, recognised: {}};

		expect(isRestricted(unknown)).toBe(false);
		expect(accepts(file('any.exe'), unknown)).toBe(true);
		expect(isRestricted(none)).toBe(true);
		expect(accepts(file('cv.pdf', 'application/pdf'), none)).toBe(false);
	});

	it('restricts nothing when the field types hold any file, however many others they list', () => {
		const anyFile: FileTypes = {tokens: ['*/*', 'application/pdf'], shown: {'*/*': [], 'application/pdf': ['.pdf']}, recognised: {'*/*': [], 'application/pdf': ['.pdf']}};

		expect(isRestricted(anyFile)).toBe(false);
		expect(accepts(file('note.eml', 'message/rfc822'), anyFile)).toBe(true);
		expect(getDisplayFormats(anyFile)).toEqual([]);
		expect(buildAcceptAttr(anyFile)).toBe('');
	});

	it('takes a file one of the field types answers', () => {
		expect(accepts(file('cv.pdf'), types)).toBe(true);
		expect(accepts(file('run.exe', 'application/x-msdownload'), types)).toBe(false);
	});
});

describe('getDisplayFormats', () => {
	it('shows each type by its shown extensions, a type without any by itself, once each', () => {
		const withUnknown: FileTypes = {...types, tokens: [...(types.tokens ?? []), 'application/x-custom', 'application/pdf']};

		expect(getDisplayFormats(withUnknown)).toEqual(['.pdf', '.qt', '.png', 'application/x-custom']);
		expect(getDisplayFormats({shown: {}, recognised: {}})).toEqual([]);
	});
});

describe('buildAcceptAttr', () => {
	it('lists every type and every recognised extension, so that the picker offers what the island takes', () => {
		expect(buildAcceptAttr(types)).toBe('application/pdf,.pdf,video/quicktime,.qt,.mov,image/*,.png');
		expect(buildAcceptAttr({tokens: [], shown: {}, recognised: {}})).toBe('');
	});
});
