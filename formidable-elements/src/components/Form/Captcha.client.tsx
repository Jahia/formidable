import {useEffect, useRef} from 'react';

type CaptchaProvider = 'turnstile' | 'hcaptcha' | 'recaptcha_v2';


const deriveProvider = (scriptUrl: string): CaptchaProvider => {
	if (scriptUrl.includes('challenges.cloudflare.com')) return 'turnstile';
	if (scriptUrl.includes('hcaptcha.com')) return 'hcaptcha';
	if (scriptUrl.includes('google.com/recaptcha')) return 'recaptcha_v2';
	return 'turnstile';
};

interface CaptchaProps {
	siteKey: string;
	scriptUrl: string;
}

export default function Captcha({siteKey, scriptUrl}: CaptchaProps) {
	const containerRef = useRef<HTMLDivElement>(null);
	const tokenRef = useRef<HTMLInputElement>(null);
	const widgetIdRef = useRef<string | undefined>(undefined);

	useEffect(() => {
		const provider = deriveProvider(scriptUrl);
		const setToken = (token: string) => { if (tokenRef.current) tokenRef.current.value = token; };
		const clearToken = () => { if (tokenRef.current) tokenRef.current.value = ''; };

		const render = () => {
			const el = containerRef.current;
			if (!el) return;
			const opts: RenderOptions = {sitekey: siteKey, callback: setToken, 'expired-callback': clearToken};
			if (provider === 'turnstile' && window.turnstile) {
				widgetIdRef.current = window.turnstile.render(el, opts);
			} else if (provider === 'hcaptcha' && window.hcaptcha) {
				window.hcaptcha.render(el, opts);
			} else if (provider === 'recaptcha_v2' && window.grecaptcha) {
				window.grecaptcha.render(el, opts);
			}
		};

		const scriptId = `fmdb-captcha-${provider}`;
		if (document.getElementById(scriptId)) {
			render();
		} else {
			const script = document.createElement('script');
			script.id = scriptId;
			script.src = scriptUrl;
			script.async = true;
			script.defer = true;
			script.onload = render;
			document.head.appendChild(script);
		}

		return () => {
			if (provider === 'turnstile' && widgetIdRef.current) {
				window.turnstile?.remove(widgetIdRef.current);
				widgetIdRef.current = undefined;
			}
		};
	}, [siteKey, scriptUrl]);

	return (
		<div className="fmdb-form-group fmdb-captcha">
			<div ref={containerRef}/>
			<input ref={tokenRef} type="hidden" name="fmdb-captcha-token" data-fmdb-captcha/>
		</div>
	);
}

