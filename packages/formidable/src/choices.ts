/** One option of a choice field, as its `options` property stores it: a JSON object per value. */
export interface ParsedChoice {
	value: string;
	label: string;
	selected: boolean;
}

// An entry that is not a {value, label} object renders as a label naming the problem, with an
// empty value: the contributor sees the broken option instead of losing it in silence.
export const parseChoices = (choices: readonly string[] = []): ParsedChoice[] => {
	return choices.map(choice => {
		try {
			const parsed = JSON.parse(choice);
			if (parsed && typeof parsed.value === "string" && typeof parsed.label === "string") {
				return {
					value: parsed.value,
					label: parsed.label,
					selected: parsed.selected === true
				};
			}
		} catch (error) {
			console.error(`Failed to parse choice JSON: ${choice}`, error);
			return {value: "", label: `Invalid JSON: ${choice}`, selected: false};
		}
		return {value: "", label: `Malformed option structure - Expected {value, label, selected}: ${choice}`, selected: false};
	});
};
