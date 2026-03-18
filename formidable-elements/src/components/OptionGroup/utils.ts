export interface OptionItem {
	value: string;
	label: string;
	selected?: boolean;
}

export const parseOptions = (options: string[] = []): OptionItem[] => {
	return options.map(option => {
		try {
			const parsed = JSON.parse(option);
			if (parsed && typeof parsed.value === 'string' && typeof parsed.label === 'string') {
				return {
					value: parsed.value,
					label: parsed.label,
					selected: parsed.selected === true
				};
			}
		} catch (error) {
			console.error(`Failed to parse option JSON: ${option}`, error);
			return {value: "", label: `Invalid JSON: ${option}`};
		}

		return {value: "", label: `Malformed option structure - Expected {value, label, selected}: ${option}`};
	});
};

