/**
 * Replaces ${fieldName} placeholders with submitted field values (TS port of the engine's
 * TemplateInterpolator). The first value of a multi-valued field is used; unknown fields resolve to
 * an empty string. Values pass through the given escaper.
 */
export const interpolate = (
	template: string | null | undefined,
	parameters: Record<string, string[]>,
	valueEscaper: (value: string) => string,
): string => {
	if (template == null) {
		return "";
	}
	return template.replace(/\$\{([^}]+)}/g, (_match, field: string) =>
		valueEscaper(parameters[field]?.[0] ?? ""),
	);
};
