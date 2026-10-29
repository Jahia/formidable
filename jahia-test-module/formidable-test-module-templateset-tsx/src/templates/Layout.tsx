import { AddResources, buildModuleFileUrl, useServerContext } from "@jahia/javascript-modules-library";
import type { ReactNode } from "react";
import "~/design/registration-yellow-theme.css";

/**
 * Places `children` in an html page, with the template set's stylesheet: the form theme written
 * against the styling contract (docs/styling/), which Vite bundles into dist/assets/style.css.
 */
export const Layout = ({ title, children }: { title: string; children: ReactNode }) => {
  const { currentResource } = useServerContext();
  const lang = currentResource.getLocale().getLanguage();
  return (
    <html lang={lang}>
      <head>
        <meta charSet="utf-8" />
        <title>{title}</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
      </head>
      <body>{children}</body>
    </html>
  );
};
