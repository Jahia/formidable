import {useEffect, useImperativeHandle, useRef} from 'react';

export interface CaptchaHandle {
	getToken: () => string;
	reset: () => void;
}

interface CaptchaProps {
	siteKey: string;
	widgetVar: string;
	onVerify?: () => void;
	onExpire?: () => void;
	ref?: React.Ref<CaptchaHandle>;
}

export default function Captcha({siteKey, widgetVar, onVerify, onExpire, ref}: CaptchaProps) {
	const containerRef = useRef<HTMLDivElement>(null);
	const tokenRef = useRef('');
	const widgetIdRef = useRef<string | undefined>(undefined);

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

		const api = (window as unknown as Record<string, unknown>)[widgetVar] as CaptchaWidgetApi | undefined;
		if (!api?.render) {
			console.warn(`[Formidable] Captcha widget "window.${widgetVar}" not found. Check captchaWidgetVar in your configuration.`);
			return;
		}

		const opts: RenderOptions = {
			'sitekey': siteKey,
			'callback': token => { tokenRef.current = token; onVerify?.(); },
			'expired-callback': () => { tokenRef.current = ''; onExpire?.(); },
		};

		widgetIdRef.current = String(api.render(el, opts));

		return () => {
			if (widgetIdRef.current) {
				api.remove?.(widgetIdRef.current);
				widgetIdRef.current = undefined;
			}
		};
	}, [siteKey, widgetVar]);

	return (
		<div className="fmdb-form-group fmdb-captcha">
			<div ref={containerRef}/>
		</div>
	);
}
