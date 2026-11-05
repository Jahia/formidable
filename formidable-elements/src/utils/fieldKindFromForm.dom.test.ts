// @vitest-environment jsdom
import {describe, expect, it} from 'vitest';
import {fieldKindFromForm, interpolateMessage} from './messageUtils';

import '~/utils/testSupport/cssEscape';

const formOf = (html: string): HTMLFormElement => {
	document.body.innerHTML = `<form>${html}</form>`;
	return document.querySelector('form')!;
};

describe('fieldKindFromForm (DOM)', () => {
	it('resolves a mirrored control through the wrapper node type', () => {
		// The slider's named control is a hidden mirror: reading the control's type
		// returned 'hidden' and made the range formatting unreachable — the wrapper's
		// data-fmdb-node-type is what must win.
		const form = formOf(`
			<div data-fmdb-node-name="budget" data-fmdb-node-type="fmdb:inputRange">
				<input type="range" id="budget-slider" value="50000"/>
				<input type="hidden" name="budget" value="50000"/>
			</div>`);

		expect(fieldKindFromForm(form)('budget')).toBe('range');
	});

	it('falls back to the control type for an unknown node type', () => {
		// A third-party field carries a node type the map does not know: the control's
		// own type keeps the formatting alive on the extension path.
		const form = formOf(`
			<div data-fmdb-node-name="rating" data-fmdb-node-type="mymod:rating">
				<input type="number" name="rating" value="4"/>
			</div>`);

		expect(fieldKindFromForm(form)('rating')).toBe('number');
	});

	it('leaves a plain text field unformatted', () => {
		const form = formOf(`
			<div data-fmdb-node-name="zip" data-fmdb-node-type="fmdb:inputText">
				<input type="text" name="zip" value="75001"/>
			</div>`);

		expect(fieldKindFromForm(form)('zip')).toBe('text');
	});

	it('reads a password control as a secret, even inside the wrapper of a third-party node type', () => {
		// the password field of formidable-extended-inputs has its own node type, unknown here:
		// the control's type is what tells it is a secret, and no message prints it
		const form = formOf(`
			<div data-fmdb-node-name="email" data-fmdb-node-type="fmdb:inputEmail"><input type="email" name="email" value="ada@example.com"></div>
			<div data-fmdb-node-name="password" data-fmdb-node-type="fmdbext:password"><input type="password" name="password" value="S3cret!pw"></div>`);

		expect(fieldKindFromForm(form)('password')).toBe('password');
		expect(interpolateMessage('${email} / ${password}', new FormData(form), 'en-US', fieldKindFromForm(form)))
			.toBe('ada@example.com / ');
	});

	it('reads a sensitive field as a secret whatever its control type: a PIN in a text input', () => {
		// a third-party sensitive type renders a text or tel control (a PIN, a show/hide toggle):
		// the wrapper's data-fmdb-sensitive, written from fmdbmix:sensitiveField, is what tells
		const form = formOf(`
			<div data-fmdb-node-name="pin" data-fmdb-node-type="mymod:pin" data-fmdb-sensitive="true">
				<input type="tel" name="pin" value="4821"/>
			</div>`);

		expect(fieldKindFromForm(form)('pin')).toBe('password');
		expect(interpolateMessage('PIN: ${pin}', new FormData(form), 'en-US', fieldKindFromForm(form))).toBe('PIN: ');
	});

	it('reads a name as a secret when any of the fields sharing it is sensitive', () => {
		// two fields submit under one name: the name submits the values of both, the secret among them
		const form = formOf(`
			<div data-fmdb-node-name="code" data-fmdb-node-type="fmdb:inputText"><input type="text" name="code" value="ABC"/></div>
			<div data-fmdb-node-name="code" data-fmdb-node-type="mymod:pin" data-fmdb-sensitive="true"><input type="text" name="code" value="4821"/></div>`);

		expect(fieldKindFromForm(form)('code')).toBe('password');
		expect(interpolateMessage('Code: ${code}', new FormData(form), 'en-US', fieldKindFromForm(form))).toBe('Code: ');
	});

	it('reads a name as a secret when its password control is not the first control of the name', () => {
		const form = formOf(`
			<input type="text" name="secret" value="visible"/>
			<input type="password" name="secret" value="S3cret!pw"/>`);

		expect(fieldKindFromForm(form)('secret')).toBe('password');
	});
});
