import {type FormEvent, useEffect, useState} from 'react';
import {interpolateMessage} from '~/utils/messageUtils';
import clsx from "clsx";
import classes from './Form.client.module.css';
import {type FormProps} from './types';
import Spinner from '~/design/Spinner';
import {type default as DOMPurifyType} from 'dompurify';
import {useTranslation} from "react-i18next";

/* eslint-disable @eslint-react/dom/no-dangerously-set-innerhtml -- Safe: content is sanitized */
export default function Form({
															 intro,
															 submissionMessage,
															 errorMessage,
															 customTarget,
															 showResetBtn = false,
															 showNewFormBtn = false,
															 showTryAgainBtn = false,
															 submitBtnLabel,
															 resetBtnLabel,
															 newFormBtnLabel,
															 tryAgainBtnLabel,
															 formId,
															 locale,
															 children
														 }: FormProps) {
	const [message, setMessage] = useState<string | null>(null);
	const [messageType, setMessageType] = useState<'success' | 'error' | null>(null);
	const [isLoading, setIsLoading] = useState(false);

	const {t} = useTranslation('formidable', {keyPrefix: 'fmdb_form'});
	const [dompurify, setDompurify] = useState<typeof DOMPurifyType | null>(null);

	useEffect(() => {
		// This library only works client-side, import it dynamically in an effect
		import("dompurify").then(({default: purify}) => {
			setDompurify(() => purify);
		});
	}, []);

	const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
		event.preventDefault();
		setIsLoading(true);

		const form = event.currentTarget;

		try {
			const formData = new FormData(form);

			// Interpolate variables in messages with locale
			const interpolatedSubmissionMessage = interpolateMessage(submissionMessage, formData, locale);

			// Use custom target if provided, otherwise use current URL
			const submitUrl = customTarget || form.action || window.location.href;

			// Simulate form submission (replace with actual submission logic)
			const response = await fetch(submitUrl, {
				method: 'POST',
				body: formData
			});

			// Add a small delay to show the spinner
			await new Promise(resolve => setTimeout(resolve, 500));

			if (response.ok) {
				setMessage(interpolatedSubmissionMessage || 'Form submitted successfully!');
				setMessageType('success');
				form.reset();
			} else {
				throw new Error('Submission failed');
			}
		} catch (error) {
			const formData = new FormData(form);
			const interpolatedErrorMessage = interpolateMessage(errorMessage, formData, locale);
			setMessage(interpolatedErrorMessage || 'An error occurred while submitting the form.');
			setMessageType('error');
			console.error("formidable submit error:", error);
		} finally {
			setIsLoading(false);
		}
	};

	const showForm = () => {
		setIsLoading(true);
		// Add transition delay
		setTimeout(() => {
			setMessage(null);
			setMessageType(null);
			setIsLoading(false);
		}, 300);
	};

	// Show message after form submission
	const hasMessage = message && messageType;

	// Sanitize HTML content to prevent XSS attacks
	const sanitizedIntro = intro && dompurify ? dompurify.sanitize(intro) : '';
	const sanitizedMessage = message && dompurify ? dompurify.sanitize(message) : '';

	return (
		<>
			{/* Loading Spinner */}
			{isLoading && (
				<Spinner
					overlay
					text={messageType === null ? t('submitting') : t('loading')}
					className="fmdb-spinner"
				/>
			)}

			{hasMessage && !isLoading &&
				<div className={clsx(`fmdb-message fmdb-message-${messageType}`, classes.message)} role="alert">
					<div className="fmdb-message-content">
						<div dangerouslySetInnerHTML={{__html: sanitizedMessage}}/>
						{messageType === 'success' && showNewFormBtn && (
							//form.reset() is done in handle submit after successful submission
							<button
								type="button"
								className="fmdb-btn fmdb-btn-secondary fmdb-new-form-btn"
								onClick={showForm}
							>
								{newFormBtnLabel || t('newFormBtn')}
							</button>
						)}
						{messageType === 'error' && showTryAgainBtn && (
							//no form.reset() show again the form with previous data
							//user can correct the data and resubmit
							<button
								type="button"
								className="fmdb-btn fmdb-btn-secondary fmdb-new-form-btn"
								onClick={showForm}
							>
								{tryAgainBtnLabel || t('tryAgainBtn')}
							</button>
						)}
					</div>
				</div>
			}
			<form
				className={clsx("fmdb-form", classes.form, (hasMessage || isLoading) && classes.hidden)}
				method="post"
				action={customTarget}
				id={formId}
				onSubmit={handleSubmit}
			>
				{/* Form introduction text */}
				{intro && (
					<header className="fmdb-form-intro" dangerouslySetInnerHTML={{__html: sanitizedIntro}}/>
				)}

				{/* Render form elements placeholder - actual elements are server-rendered */}
				{children}

				{/* Form submission buttons */}
				<div className="fmdb-form-actions">
					<button type="submit" className="fmdb-btn fmdb-btn-primary" disabled={isLoading}>
						{submitBtnLabel || t('submitBtn')}
					</button>
					{showResetBtn && (
						<button type="reset" className="fmdb-btn fmdb-btn-secondary" disabled={isLoading}>
							{resetBtnLabel || t('resetBtn')}
						</button>
					)}
				</div>
			</form>

		</>

	);
}
