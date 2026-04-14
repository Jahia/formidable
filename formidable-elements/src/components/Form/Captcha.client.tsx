import {useEffect, useImperativeHandle, useRef} from 'react';
import {type CaptchaProvider} from './types';

export interface CaptchaHandle {
	getToken: () => string;
	reset: () => void;
}

interface CaptchaProps {
	siteKey: string;
	provider: CaptchaProvider;
	ref?: React.Ref<CaptchaHandle>;
}

export default function Captcha({siteKey, provider, ref}: CaptchaProps) {
	const containerRef = useRef<HTMLDivElement>(null);
	const tokenRef = useRef('');
	const widgetIdRef = useRef<string | undefined>(undefined);

	useImperativeHandle(ref, () => ({
		getToken: () => tokenRef.current,
		reset: () => {
			tokenRef.current = '';
			if (provider === 'turnstile' && widgetIdRef.current) {
				window.turnstile?.reset(widgetIdRef.current);
			}
		}
	}), [provider]);

	useEffect(() => {
		const el = containerRef.current;
		if (!el) return;

		const opts: RenderOptions = {
			'sitekey': siteKey,
			'callback': token => { tokenRef.current = token; },
			'expired-callback': () => { tokenRef.current = ''; },
		};

		// Script is injected server-side with defer — always ready by the time the Island hydrates.
		if (provider === 'turnstile' && window.turnstile) {
			widgetIdRef.current = window.turnstile.render(el, opts);
		} else if (provider === 'hcaptcha' && window.hcaptcha) {
			window.hcaptcha.render(el, opts);
		} else if (provider === 'recaptcha_v2' && window.grecaptcha) {
			window.grecaptcha.render(el, opts);
		}

		return () => {
			if (provider === 'turnstile' && widgetIdRef.current) {
				window.turnstile?.remove(widgetIdRef.current);
				widgetIdRef.current = undefined;
			}
		};
	}, [siteKey, provider]);

	return (
		<div className="fmdb-form-group fmdb-captcha">
			<div ref={containerRef}/>
		</div>
	);
}

