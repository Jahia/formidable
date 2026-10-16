// @vitest-environment jsdom
import {describe, expect, it} from 'vitest';
import '~/utils/testSupport/cssEscape';
import {fieldKindFromForm, interpolateMessage} from './messageUtils';

const formOf = (html: string): HTMLFormElement => {
	document.body.innerHTML = `<form>${html}</form>`;
	return document.querySelector('form')!;
};

describe('fieldKindFromForm', () => {
	it('reads a password control as a secret, even inside a wrapper of a third-party node type', () => {
		// jahia-private#5909 finding 7: the password field of formidable-extended-inputs has its own node type,
		// unknown here; the control's type is what tells it is a secret.
		const form = formOf(`
			<div data-fmdb-node-name="email" data-fmdb-node-type="fmdb:inputEmail"><input type="email" name="email" value="ada@example.com"></div>
			<div data-fmdb-node-name="password" data-fmdb-node-type="fmdbext:password"><input type="password" name="password" value="S3cret!pw"></div>`);

		expect(fieldKindFromForm(form)('password')).toBe('password');
		expect(interpolateMessage('${email} / ${password}', new FormData(form), 'en-US', fieldKindFromForm(form)))
			.toBe('ada@example.com / ');
	});
});
