import clsx from 'clsx';
import classes from './Spinner.module.css';

export interface SpinnerProps {
	size?: 'small' | 'medium' | 'large';
	text?: string;
	className?: string;
	overlay?: boolean;
}

export default function Spinner({
																	size = 'medium',
																	text,
																	className,
																	overlay = false
																}: SpinnerProps) {
	const spinnerElement = (
		<div className={clsx(classes.spinner, className)}>
			<div className={clsx(classes.circle, classes[size])}/>
			{text && (
				<span className={clsx(classes.text, classes[`text-${size}`])}>
					{text}
				</span>
			)}
		</div>
	);

	if (overlay) {
		return (
			<div className={classes.overlay}>
				{spinnerElement}
			</div>
		);
	}

	return spinnerElement;
}
