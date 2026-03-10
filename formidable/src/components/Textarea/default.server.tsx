import {jahiaComponent} from "@jahia/javascript-modules-library";

interface TextareaProps {
	"jcr:title"?: string;
	placeholder?: string;
	defaultValue?: string;
	minLength?: number;
	maxLength?: number;
	rows?: number;
	cols?: number;
	// Advanced settings from mixin
	required?: boolean;
	autocomplete?: string;
	spellcheck?: boolean;
	readonly?: boolean;
	autofocus?: boolean;
	disabled?: boolean;
	form?: string;
	dirname?: string;
	wrap?: 'soft' | 'hard' | 'off';
	resize?: 'none' | 'both' | 'horizontal' | 'vertical';
}

jahiaComponent(
	{
		componentType: "view",
		nodeType: "fmdb:textarea",
		name: "default"
	},
	(
		{
			"jcr:title": label,
			placeholder,
			defaultValue,
			minLength,
			maxLength,
			rows = 4,
			cols,
			wrap = 'soft',
			resize = 'vertical',
			required,
			autocomplete,
			spellcheck = true,
			readonly,
			autofocus,
			disabled,
			form,
			dirname
		}: TextareaProps,
		{currentNode}
	) => {

		// Generate unique id and name for the textarea
		const textareaId = `textarea-${currentNode.getIdentifier()}`;
		const textareaName = currentNode.getName();

		// Build style object for resize control
		const textareaStyle: React.CSSProperties = {
			resize: resize
		};

		return (
			<div className="fmdb-form-group">
				{label && (
					<label htmlFor={textareaId} className="fmdb-form-label">
						{label}
						{required && <span className="fmdb-required-indicator">*</span>}
					</label>
				)}

				<textarea
					id={textareaId}
					name={textareaName}
					className="fmdb-form-control"
					placeholder={placeholder}
					defaultValue={defaultValue}
					rows={rows}
					cols={cols}
					wrap={wrap}
					minLength={minLength}
					maxLength={maxLength}
					required={required}
					autoComplete={autocomplete}
					spellCheck={spellcheck}
					style={textareaStyle}
					readOnly={readonly}
					autoFocus={autofocus}
					disabled={disabled}
					form={form}
					dir={dirname}
				/>

				{/* Character count indicator if maxLength is set */}
				{maxLength && (
					<div className="fmdb-form-hint">
						Maximum {maxLength} characters
					</div>
				)}
			</div>
		);
	}
);
