import {useEffect, useImperativeHandle, useRef} from 'react';

export interface CaptchaHandle {
	getToken: () => string;
	reset: () => void;
}

interface CaptchaProps {
	siteKey: string;
	widgetVar: string;
	widgetTimeoutSeconds?: number;
	onVerify?: () => void;
	onExpire?: () => void;
	ref?: React.Ref<CaptchaHandle>;
}

const DEFAULT_WIDGET_TIMEOUT_SECONDS = 5;
const WIDGET_POLL_INTERVAL_MS = 100;

export default function Captcha({siteKey, widgetVar, widgetTimeoutSeconds, onVerify, onExpire, ref}: CaptchaProps) {
	const containerRef = useRef<HTMLDivElement>(null);
	const tokenRef = useRef('');
	// Kept as returned by the provider: Google reCAPTCHA ids are numbers (0 for the
	// first widget), Turnstile ids are strings; reset()/remove() expect the native type.
	const widgetIdRef = useRef<string | number | undefined>(undefined);

	useImperativeHandle(ref, () => ({
		getToken: () => tokenRef.current,
		reset: () => {
			tokenRef.current = '';
			onExpire?.();
			const api = (window as unknown as Record<string, unknown>)[widgetVar] as CaptchaWidgetApi | undefined;
			api?.reset?.(widgetIdRef.current);
		}
	}), [widgetVar, onExpire]);

	useEffect(() => {
		const el = containerRef.current;
		if (!el) return;

		// Some provider scripts expose their API asynchronously: Google's api.js is a
		// two-stage loader whose render() only exists once a second script has loaded.
		// Poll until the API is available instead of sampling once at hydration time.
		// Clamp the budget to a finite positive number: a NaN deadline would never
		// satisfy Date.now() >= deadline and the poll would run forever.
		const timeoutSeconds = typeof widgetTimeoutSeconds === 'number' && Number.isFinite(widgetTimeoutSeconds) && widgetTimeoutSeconds > 0
			? widgetTimeoutSeconds
			: DEFAULT_WIDGET_TIMEOUT_SECONDS;
		const deadline = Date.now() + timeoutSeconds * 1000;
		let pollTimer: ReturnType<typeof setTimeout> | undefined;

		const tryRender = () => {
			const api = (window as unknown as Record<string, unknown>)[widgetVar] as CaptchaWidgetApi | undefined;
			if (api?.render) {
				const opts: RenderOptions = {
					'sitekey': siteKey,
					'callback': token => { tokenRef.current = token; onVerify?.(); },
					'expired-callback': () => { tokenRef.current = ''; onExpire?.(); },
				};
				widgetIdRef.current = api.render(el, opts);
				return;
			}

			if (Date.now() >= deadline) {
				console.warn(`[Formidable] Captcha widget "window.${widgetVar}" still not available after ${timeoutSeconds}s. Check captchaWidgetVar and captchaScriptUrl in your configuration.`);
				return;
			}

			pollTimer = setTimeout(tryRender, WIDGET_POLL_INTERVAL_MS);
		};

		tryRender();

		return () => {
			clearTimeout(pollTimer);
			// Compare against undefined: Google's first widget id is the number 0.
			if (widgetIdRef.current !== undefined) {
				const api = (window as unknown as Record<string, unknown>)[widgetVar] as CaptchaWidgetApi | undefined;
				api?.remove?.(widgetIdRef.current);
				widgetIdRef.current = undefined;
			}
		};
	}, [siteKey, widgetVar, widgetTimeoutSeconds]);

	return (
		<div className="fmdb-form-group fmdb-captcha">
			<div ref={containerRef}/>
		</div>
	);
}
