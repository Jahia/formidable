/// <reference types="vite/client" />

/**
 * GraalJS host interop, available in server bundles only (the JS engine runs with full host class
 * lookup). Used by form actions to instantiate Jahia platform classes.
 */
declare const Java: {
	// Host classes have no generated typings; callers get an untyped constructor/namespace.
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	type(className: string): any;
};

interface RenderOptions {
	"sitekey": string;
	"callback": (token: string) => void;
	"expired-callback": () => void;
}

interface CaptchaWidgetApi {
	render: (el: HTMLElement, opts: RenderOptions) => string | number;
	remove?: (id: string | undefined) => void;
	reset?: (id: string | undefined) => void;
}
