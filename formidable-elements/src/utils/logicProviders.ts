/**
 * Conditional logic sources that are not another form field.
 *
 * A field source is read from the DOM and yields a list of values, so it stays in the
 * evaluator. Every other source designates one thing outside the form whose current state
 * is a single optional string: a JS variable (a datalayer entry), a URL parameter, a
 * cookie. They therefore share one shape, one operator set and one evaluation path — a new
 * provider is a `read` function and, when the value can change without a form event, a
 * `subscribe`.
 *
 * This registry is internal to the module: providers ship with Formidable. Nothing here is
 * a public extension point yet (see .harness/spec-phase3-logic-providers-and-pluggable-operators.md).
 */

/** Config keys a provider rule may carry in its stored JSON, one per provider. */
export type LogicProviderConfigKey = 'variable' | 'param' | 'cookie';

/** What a rule designates: another form field, or one of the providers below. */
export type LogicSourceType = 'field' | 'jsVariable' | 'urlParam' | 'cookie';

export interface ScalarLogicProvider {
	/** Persisted verbatim as the rule's `sourceType`. */
	id: Exclude<LogicSourceType, 'field'>;
	/** The rule property holding what this provider designates. */
	configKey: LogicProviderConfigKey;
	/** Current value of the designated thing, or undefined when it does not exist. */
	read(ref: string): string | undefined;
	/**
	 * Notifies when any of the designated things may have changed, for state that moves
	 * without a form event. Returns an unsubscribe function. Providers whose value is
	 * fixed for the page lifetime omit it.
	 */
	subscribe?(refs: string[], onChange: () => void): () => void;
}

const JS_VARIABLE_PATH_PATTERN = /^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*$/;

/**
 * Safely resolves a dotted variable path (e.g. "window.cxs.profileProperties.firstName")
 * against the window object. Returns undefined when any segment is missing or the
 * path is not a plain dotted identifier chain.
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
	// A cookie can be written by another tab or by a consent banner, neither of which
	// raises an event here; re-reading when the tab becomes visible again covers the
	// common case without a timer.
	subscribe: (_refs, onChange) => {
		const handler = () => {
			if (document.visibilityState === 'visible') onChange();
		};

		document.addEventListener('visibilitychange', handler);
		return () => document.removeEventListener('visibilitychange', handler);
	}
};

const PROVIDERS: ScalarLogicProvider[] = [jsVariableProvider, urlParamProvider, cookieProvider];

const PROVIDERS_BY_ID = new Map<string, ScalarLogicProvider>(
	PROVIDERS.map(provider => [provider.id, provider])
);

export const getLogicProvider = (sourceType?: string): ScalarLogicProvider | undefined =>
	sourceType === undefined || sourceType === '' || sourceType === 'field'
		? undefined
		: PROVIDERS_BY_ID.get(sourceType);

export const logicProviderConfigKeys = (): LogicProviderConfigKey[] =>
	PROVIDERS.map(provider => provider.configKey);

/**
 * Event any integrator can dispatch on a form (or on the document, since it bubbles) to
 * have its conditional logic re-evaluated — after pushing to a datalayer, after a consent
 * banner is answered, after a client-side route change. It is the exact mechanism where
 * sampling is only an approximation.
 */
export const FORM_LOGIC_INVALIDATE_EVENT = 'fmdb:logic-invalidate';
