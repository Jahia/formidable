import {defineConfig} from "vite";
import jahia from "@jahia/vite-plugin";
import {spawnSync} from "node:child_process";
import path from "node:path";
import sbom from "rollup-plugin-sbom";

export default defineConfig({
  resolve: {
    alias: {"~": path.resolve("./src")},
  },
  build: {
    sourcemap: true,
  },
  plugins: [
    sbom({specVersion: "1.4"}),
    jahia({
      // This function is called every time a build succeeds in watch mode
      watchCallback() {
        spawnSync("yarn", ["watch:callback"], {stdio: "inherit", shell: true});
      },
    }),
  ],
});
