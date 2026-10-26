import { defineConfig } from "vite";
import jahia from "@jahia/vite-federation-plugin";

export default defineConfig({
  build: {
    outDir: "./src/main/resources/javascript/apps/",
  },
  // Apollo Client runs in development mode unless told otherwise, and then deep-freezes every query
  // result: shared with jContent, it makes jContent's in-place sorts of those results throw
  define: {
    "globalThis.__DEV__": "false",
  },
  plugins: [
    jahia({
      exposes: {
        "./init": "./src/javascript/init.tsx",
      },
    }),
  ],
});
