import {useServerContext} from "@jahia/javascript-modules-library";
import type {JSX, ReactNode} from "react";

/**
 * Page layout for the form template: a white page holding the hidden.pageBuilder view of the form
 * (src/design/page.css, shipped in the form's own stylesheet, which the form view loads
 * through AddResources — nothing is loaded here).
 */
export const Layout = ({title, className, children}: {
	title?: string;
	className?: string;
	children: ReactNode;
}): JSX.Element => {
	const {currentResource} = useServerContext();

	return (
		<html lang={currentResource.getLocale().getLanguage()}>
			<head>
				<meta charSet="utf-8"/>
				<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
				{title && <title>{title}</title>}
			</head>
			<body className="fmdb-form-page-body">
				<main id="main" className={className}>{children}</main>
			</body>
		</html>
	);
};
