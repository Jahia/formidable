export interface ParsedChoice {
	value: string;
	label: string;
	selected: boolean;
}

export const parseChoices = (choices: string[] = []): ParsedChoice[] => {
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

