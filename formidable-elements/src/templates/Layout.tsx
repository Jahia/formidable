import type {JSX, ReactNode} from "react";

/**
 * Minimal page layout for the form template: a form opened as a page (jContent Page
 * Builder) only needs a head and a body. The form view brings its own stylesheet and
 * scripts through AddResources, so nothing is loaded here.
 */
export const Layout = ({title, className, children}: {
	title?: string;
	className?: string;
	children: ReactNode;
}): JSX.Element => (
	<>
		<head>
			<meta charSet="utf-8"/>
			<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
			{title && <title>{title}</title>}
		</head>
		<body>
			<main id="main" className={className}>{children}</main>
		</body>
	</>
);
