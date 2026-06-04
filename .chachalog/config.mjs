/// @ts-check
/// <reference types="@chachalog/types" />
import fs from "node:fs";
import { defineConfig } from "chachalog";
import github from "chachalog/github";

export default defineConfig(() => ({
	allowedBumps: ["patch", "minor", "major"],
	platform: github({
		base: "main"
	}),
    managers: yarn(),
}));
