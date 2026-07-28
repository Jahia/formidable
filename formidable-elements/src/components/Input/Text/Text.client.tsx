import {useMask} from "~/hooks/useMask";

interface MaskedTextInputProps {
	mask: string;
	defaultValue?: string;
	/** All input attributes are computed server-side and must stay serialisable. */
	inputAttributes: Record<string, string | number | boolean | undefined>;
}

/**
 * Text input hydrated only when a mask is configured.
 * The server pre-formats the default value and derives the HTML `pattern`
 * attribute, so native validation keeps working without JavaScript.
 */
export default function MaskedTextInput({mask, defaultValue, inputAttributes}: MaskedTextInputProps) {
	const {inputRef, handleInput} = useMask({mask});

	return (
		<input
			{...inputAttributes}
			ref={inputRef}
			defaultValue={defaultValue}
			onInput={handleInput}
		/>
	);
}
