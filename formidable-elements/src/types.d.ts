/// <reference types="vite/client" />

interface RenderOptions {
	sitekey: string;
	callback: (token: string) => void;
	'expired-callback': () => void;
}

interface Window {
	turnstile?: {render: (el: HTMLElement, opts: RenderOptions) => string; remove: (id: string) => void};
	hcaptcha?: {render: (el: HTMLElement, opts: RenderOptions) => string};
	grecaptcha?: {render: (el: HTMLElement, opts: RenderOptions) => number};
}

