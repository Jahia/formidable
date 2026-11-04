// The contract of a conditional-logic source that a module other than Formidable adds
// (docs/adr/0001-extension-modules-register-logic-sources.md). A source has two halves,
// registered apart because they run apart: the editor half in jContent, through the
// `@jahia/ui-extender` registry, and the page half in the visitor's browser, through the
// queue below. Both carry the same id and the same config key.
//
// This entry point holds no React and no runtime of its own, so a jContent bundle on
// React 18 and a plain script of a page both read it.

/** What a rule on a source may test: its state is one optional string. */
export type LogicSourceOperator = "equals" | "notEquals" | "contains" | "exists" | "notExists";

/** The registry type the editor half is registered under: `registry.add(LOGIC_SOURCE_REGISTRY_TYPE, id, entry)`. */
export const LOGIC_SOURCE_REGISTRY_TYPE = "formidableLogicSource";

/**
 * The global array the page half is pushed onto: `(window.formidableLogicSources ??= []).push(source)`.
 * The page script of a source may run before or after Formidable's: once Formidable's runs, a push
 * registers the source at once and asks the forms to evaluate their rules again.
 */
export const PAGE_LOGIC_SOURCES_GLOBAL = "formidableLogicSources";

/**
 * A source id is `<module>:<name>`: it is stored in every rule, and the module part keeps two
 * modules from colliding and names the module a stored rule needs.
 */
export const LOGIC_SOURCE_ID_PATTERN = /^[A-Za-z0-9][\w-]*:[\w-]+$/;

/**
 * Rule keys that are not a source's configuration, so no source may take one as its config key:
 * a rule's configuration is the one other string key it carries, which is how the page and the
 * server read a rule without knowing its source.
 */
export const SHARED_RULE_KEYS: readonly string[] = [
	"logicId",
	"sourceType",
	"sourceNodeId",
	"sourceFieldKey",
	"sourceFieldName",
	"sourceFieldType",
	"valueKind",
	"operator",
	"value",
	"values",
];

/** One entry of a source's reference dropdown: what the rule stores, and what the author reads. */
export interface LogicSourceRefChoice {
	value: string;
	label: string;
}

/** What a source's reference list answered; `available: false` keeps the text input. */
export interface LogicSourceRefChoices {
	available: boolean;
	choices: LogicSourceRefChoice[];
}

/**
 * The editor half: everything the rule row needs, in jContent. Its id is the registry key. A type
 * rather than an interface, so that it is a `Service` of the `@jahia/ui-extender` registry.
 */
export type EditorLogicSource = {
	/** The rule key holding what the source designates. */
	configKey: string;
	/** i18n keys, namespace included (`my-module:…`): the source's module ships them. */
	labelKey: string;
	configLabelKey: string;
	configPlaceholderKey: string;
	/** What a rule on this source may test, the first being the default of a new rule. */
	operators: LogicSourceOperator[];
	/** Rejects a reference the page could never resolve. */
	isValidRef?: (ref: string) => boolean;
	/**
	 * Modules the site must have for the source to be offered to a new rule. A rule already stored on
	 * it stays editable where they are missing, and fails closed on the page.
	 */
	requiredModules?: string[];
	/**
	 * When the source can list the references a rule may pick: a GraphQL document, run with the
	 * variable `path` (the edited node), and how to read its answer. The row then offers a dropdown.
	 */
	refChoices?: {
		query: string;
		read: (data: unknown) => LogicSourceRefChoices;
	};
};

/** The page half: how the visitor's browser reads the source. */
export interface PageLogicSource {
	/** The same id as the editor half. */
	id: string;
	/** The same config key as the editor half. */
	configKey: string;
	/**
	 * Whether the source can be read now. False: every rule on it is false, `notExists` included,
	 * and the page declares nothing for it to the server, so the server hides the field too.
	 */
	available?(): boolean;
	/** The value of the designated thing, or undefined when it does not exist. A throw counts as unavailable. */
	read(ref: string): string | undefined;
	/** Notifies when a designated thing may have changed without a form event. Returns an unsubscribe function. */
	subscribe?(refs: string[], onChange: () => void): () => void;
}
