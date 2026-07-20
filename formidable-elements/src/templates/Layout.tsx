import type {JSX, ReactNode} from "react";
import {AddResources, buildModuleFileUrl} from "@jahia/javascript-modules-library";

export const Layout = ({
	className,
	children,
}: {
	className?: string;
	children: ReactNode;
}): JSX.Element => (
	<>
		<head>
			<meta charSet="utf-8"/>
			<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
			<AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")}/>
		</head>
		<body>
		<main id="main" className={className}>{children}</main>
		</body>
	</>
);
