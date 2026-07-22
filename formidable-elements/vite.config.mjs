import { defineConfig } from "vite";
import jahia from "@jahia/vite-plugin";
import { spawnSync } from "node:child_process";
import path from "node:path";
import sbom from "rollup-plugin-sbom";

export default defineConfig({
  resolve: {
    alias: { "~": path.resolve("./src") },
  },
  build: {
    sourcemap: true,
  },
  plugins: [
    sbom({ specVersion: "1.4" }),
    jahia({
      // Default values:
      // inputDir: "src",
      // outputDir: "dist",
      // assetsDir: "assets",
      // client: {
      //   inputGlob: "**/*.client.{jsx,tsx}",
      //   outputDir: "client",
      // },

      // Extends the default server glob (**/*.server.{jsx,tsx}) with plain .ts so the
      // form actions in src/server/actions/ are bundled.
      server: {
        inputGlob: "**/*.server.{ts,jsx,tsx}",
      },

      // This function is called every time a build succeeds in watch mode
      watchCallback() {
        spawnSync("yarn", ["watch:callback"], { stdio: "inherit", shell: true });
      },
    }),
  ],
});
