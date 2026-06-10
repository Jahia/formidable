/// <reference types="vite/client" />

interface RenderOptions {
	sitekey: string;
	callback: (token: string) => void;
	'expired-callback': () => void;
}

interface CaptchaWidgetApi {
	render: (el: HTMLElement, opts: RenderOptions) => string | number;
	remove?: (id: string | undefined) => void;
	reset?: (id: string | undefined) => void;
}
