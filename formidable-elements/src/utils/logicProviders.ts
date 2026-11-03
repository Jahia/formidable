/**
 * Conditional logic sources that are not another form field.
 *
 * A field source is read from the DOM and yields a list of values, so it stays in the
 * evaluator. Every other source designates one thing outside the form whose current state
 * is a single optional string: a JS variable (a datalayer entry), a URL parameter, a
 * cookie, a jExperience profile property. They therefore share one shape, one operator set
 * and one evaluation path — a new provider is a `read` function and, when the value can
 * change without a form event, a `subscribe`; one whose system may be missing from the page
 * adds `available`.
 *
 * This list is internal: providers ship with Formidable, and nothing here is a public
 * extension point yet. A provider that belongs to another module is already shaped as one
 * would be: its entry here only forwards to the object that module's script puts on the
 * page (see hostedProvider). The editor half lives in
 * formidable-engine/src/javascript/ConditionalLogic/providers.ts and must declare the same
 * ids and config keys; formidable-engine's providers.test.ts checks it.
 */

/** Config keys a provider rule may carry in its stored JSON, one per provider. */
export type LogicProviderConfigKey = 'variable' | 'param' | 'cookie' | 'property';

/** What a rule designates: another form field, or one of the providers below. */
export type LogicSourceType = 'field' | 'jsVariable' | 'urlParam' | 'cookie' | 'jexperience:profile';

export interface ScalarLogicProvider {
	/** Persisted verbatim as the rule's `sourceType`. */
	id: Exclude<LogicSourceType, 'field'>;
	/** The rule property holding what this provider designates. */
	configKey: LogicProviderConfigKey;
	/**
	 * Whether the source can be read at all right now. False when the system behind it is
	 * not on the page, was blocked (no consent, a bot filter) or has not loaded yet: the rule
	 * is then false whatever its operator, `notExists` included, and the reference is left out
	 * of the submit-time declaration, so the server fails closed too. Providers whose state is
	 * always readable omit it.
	 */
	available?(): boolean;
	/**
	 * Current value of the designated thing, or undefined when it does not exist. Throws when the
	 * source cannot be read after all; callers read through readLogicProvider, which tells the two apart.
	 */
	read(ref: string): string | undefined;
	/**
	 * Notifies when any of the designated things may have changed, for state that moves
	 * without a form event. Returns an unsubscribe function. Providers whose value is
	 * fixed for the page lifetime omit it.
	 */
	subscribe?(refs: string[], onChange: () => void): () => void;
}

// Segments are identifiers, or digits so array entries are reachable in dotted
// form (e.g. "dataLayer.0.event" for a GTM datalayer push).
const JS_VARIABLE_PATH_PATTERN = /^[A-Za-z_$][\w$]*(\.([A-Za-z_$][\w$]*|\d+))*$/;

/**
 * Safely resolves a dotted variable path (e.g. "window.cxs.profileProperties.firstName",
 * "dataLayer.0.event") against the window object. Returns undefined when any segment is
 * missing or the path is not a plain dotted chain of identifiers and array indexes.
 */
export const resolveJsVariableValue = (variable: string): unknown => {
	if (typeof window === 'undefined') return undefined;
	const path = variable.trim().replace(/^window\./, '');
	if (!JS_VARIABLE_PATH_PATTERN.test(path)) return undefined;

	let current: unknown = window;
	for (const segment of path.split('.')) {
		if (current === null || current === undefined) return undefined;
		current = (current as Record<string, unknown>)[segment];
	}

	return current;
};

/**
 * Builds a comparable snapshot of the current variable values so a watcher can
 * detect changes cheaply. Undefined/null are encoded distinctly from their
 * string representations.
 *
 * Only leaf values are watched reliably: an object stringifies to "[object Object]", so
 * mutations inside it produce an identical snapshot and no re-evaluation.
 */
export const getJsVariablesSnapshot = (variables: string[]): string =>
	JSON.stringify(variables.map(variable => {
		const raw = resolveJsVariableValue(variable);
		if (raw === undefined) return '\u0000undefined';
		if (raw === null) return '\u0000null';
		return String(raw);
	}));

/**
 * JS variables (datalayer entries and the like) are populated asynchronously and change
 * without any form or DOM event, and a plain object offers no change notification — there
 * is no `onChange` to attach, and instrumenting a global we do not own via
 * defineProperty/Proxy would only catch assignments and could break the page's own code.
 * So the value is sampled. Integrators who can tell us are better served by the
 * invalidation event (see FORM_LOGIC_INVALIDATE_EVENT).
 */
const JS_VARIABLE_SAMPLE_INTERVAL_MS = 100;

const jsVariableProvider: ScalarLogicProvider = {
	id: 'jsVariable',
	configKey: 'variable',
	read: ref => {
		const raw = resolveJsVariableValue(ref);
		return raw === undefined || raw === null ? undefined : String(raw);
	},
	subscribe: (refs, onChange) => {
		let lastSnapshot = getJsVariablesSnapshot(refs);
		const timer = window.setInterval(() => {
			const snapshot = getJsVariablesSnapshot(refs);
			if (snapshot !== lastSnapshot) {
				lastSnapshot = snapshot;
				onChange();
			}
		}, JS_VARIABLE_SAMPLE_INTERVAL_MS);

		return () => window.clearInterval(timer);
	}
};

const urlParamProvider: ScalarLogicProvider = {
	id: 'urlParam',
	configKey: 'param',
	// Fixed for the page: a query-string change navigates or is a history API call the
	// integrator can follow with the invalidation event. Absent parameter reads as
	// undefined, a present but valueless one ("?promo") as the empty string.
	read: ref => {
		if (typeof window === 'undefined') return undefined;
		const value = new URLSearchParams(window.location.search).get(ref);
		return value === null ? undefined : value;
	}
};

/**
 * Reads one cookie by name. Tolerant by design: values may be percent-encoded and may
 * themselves contain "=", and a malformed cookie header must never throw.
 */
const readCookie = (name: string): string | undefined => {
	if (typeof document === 'undefined') return undefined;

	for (const part of document.cookie.split(';')) {
		const separator = part.indexOf('=');
		if (separator === -1) continue;

		if (part.slice(0, separator).trim() !== name) continue;

		const raw = part.slice(separator + 1).trim();
		try {
			return decodeURIComponent(raw);
		} catch {
			return raw;
		}
	}

	return undefined;
};

const cookieProvider: ScalarLogicProvider = {
	id: 'cookie',
	configKey: 'cookie',
	read: readCookie,
	// A cookie written by another tab is noticed when this tab becomes visible again,
	// without a timer. A same-tab write (a consent banner answered on this very page)
	// never fires visibilitychange: that case needs the invalidation event below.
	subscribe: (_refs, onChange) => {
		const handler = () => {
			if (document.visibilityState === 'visible') onChange();
		};

		document.addEventListener('visibilitychange', handler);
		return () => document.removeEventListener('visibilitychange', handler);
	}
};

/** What another module's script puts on the page to serve one provider: the provider minus its identity. */
export type HostedLogicProvider = Pick<ScalarLogicProvider, 'available' | 'read' | 'subscribe'>;

/**
 * A provider whose implementation belongs to another module, found at call time on the
 * global that module's script fills — so the order in which the two scripts run does not
 * matter. Absent, or throwing, it is unavailable: the rule fails closed. That script asks for
 * a re-evaluation through FORM_LOGIC_INVALIDATE_EVENT once its state is loaded.
 */
const hostedProvider = (
	id: ScalarLogicProvider['id'],
	configKey: LogicProviderConfigKey,
	host: () => HostedLogicProvider | undefined
): ScalarLogicProvider => {
	const resolve = (): HostedLogicProvider | undefined => {
		try {
			return globalThis.window === undefined ? undefined : host();
		} catch {
			return undefined;
		}
	};

	return {
		id,
		configKey,
		available: () => {
			const hosted = resolve();
			try {
				return Boolean(hosted) && (typeof hosted!.available !== 'function' || hosted!.available() === true);
			} catch {
				return false;
			}
		},
		// A throw passes through: readLogicProvider counts it as unavailable, never as absent
		read: ref => {
			const value = resolve()?.read(ref);
			return typeof value === 'string' ? value : undefined;
		},
		subscribe: (refs, onChange) => {
			try {
				return resolve()?.subscribe?.(refs, onChange) ?? (() => undefined);
			} catch {
				return () => undefined;
			}
		}
	};
};

declare global {
	/** Filled by formidable-jexperience-engine's formidable-jxp.js, on a site jExperience tracks. */
	var formidableJxp: {logicProvider?: HostedLogicProvider} | undefined;
}

/** A property of the visitor's jExperience profile, read from the context jExperience's tracker loaded. */
const jexperienceProfileProvider = hostedProvider(
	'jexperience:profile',
	'property',
	() => globalThis.formidableJxp?.logicProvider
);

const PROVIDERS: ScalarLogicProvider[] = [jsVariableProvider, urlParamProvider, cookieProvider, jexperienceProfileProvider];

const PROVIDERS_BY_ID = new Map<string, ScalarLogicProvider>(
	PROVIDERS.map(provider => [provider.id, provider])
);

export const getLogicProvider = (sourceType?: string): ScalarLogicProvider | undefined =>
	sourceType === undefined || sourceType === '' || sourceType === 'field'
		? undefined
		: PROVIDERS_BY_ID.get(sourceType);

export const logicProviderConfigKeys = (): LogicProviderConfigKey[] =>
	PROVIDERS.map(provider => provider.configKey);

/** Whether a provider's source can be read now; see ScalarLogicProvider.available. */
export const isLogicProviderAvailable = (provider: ScalarLogicProvider): boolean =>
	typeof provider.available !== 'function' || provider.available();

/** What a read that threw returns: the source could not be read, so its rules fail closed. */
export const UNREADABLE: unique symbol = Symbol('unreadable');

/**
 * Reads a provider's reference: its value, undefined when the thing does not exist, or
 * UNREADABLE when the read threw — a source that is unavailable after all. Read as "absent",
 * a broken script would show a field gated on `notExists`, and declare it absent to the server.
 */
export const readLogicProvider = (provider: ScalarLogicProvider, ref: string): string | undefined | typeof UNREADABLE => {
	try {
		return provider.read(ref);
	} catch {
		return UNREADABLE;
	}
};

/**
 * Event any integrator can dispatch to have conditional logic re-evaluated — after pushing
 * to a datalayer, after a consent banner is answered, after a client-side route change. It
 * is the exact mechanism where sampling is only an approximation.
 *
 * Listened for on the document. Dispatch it there; dispatching on an element inside the
 * page also works, but only with `bubbles: true` (not the Event default).
 */
export const FORM_LOGIC_INVALIDATE_EVENT = 'fmdb:logic-invalidate';

/**
 * Header carrying the declared provider state at submit time: base64-encoded UTF-8 JSON
 * (header values must stay ASCII; provider values may not be). Same transport pattern as
 * the captcha token — request metadata travels in a header, never among the form's own
 * fields, so it can never reach a stored submission.
 */
export const FORM_LOGIC_STATE_HEADER = 'X-Formidable-Logic-State';
