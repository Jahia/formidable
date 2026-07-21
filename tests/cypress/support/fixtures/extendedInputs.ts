import { BaseInputData, JahiaNode, NodeProperty } from "./types";

/**
 * Node factories for the optional formidable-extended-inputs module (fmdbext: types). The module
 * must be enabled on the test site before these nodes can be created.
 */

export interface RatingData extends BaseInputData {
  icon?: "star" | "heart" | "thumb" | "number";
  maxValue?: number;
  minLabel?: string;
  maxLabel?: string;
}

export interface ScaleData extends BaseInputData {
  minValue?: number;
  maxValue?: number;
  step?: number;
  minLabel?: string;
  maxLabel?: string;
}

export interface SwitchData extends BaseInputData {
  displayMode?: "toggle" | "buttons";
  onLabel?: string;
  offLabel?: string;
  defaultState?: boolean;
}

export interface ConsentData extends BaseInputData {
  statement?: string;
  termsLinkLabel?: string;
}

function baseProperties(data: BaseInputData): NodeProperty[] {
  const properties: NodeProperty[] = [];
  if (data.title) properties.push({ name: "jcr:title", value: data.title, language: "en" });
  if (data.helpText) properties.push({ name: "helpText", value: data.helpText, language: "en" });
  if (data.required !== undefined)
    properties.push({ name: "required", value: String(data.required), type: "BOOLEAN" });
  return properties;
}

export function getRatingNode(data: RatingData = {}): JahiaNode {
  const properties = baseProperties(data);
  if (data.icon) properties.push({ name: "icon", value: data.icon });
  if (data.maxValue !== undefined)
    properties.push({ name: "maxValue", value: String(data.maxValue), type: "LONG" });
  if (data.minLabel) properties.push({ name: "minLabel", value: data.minLabel, language: "en" });
  if (data.maxLabel) properties.push({ name: "maxLabel", value: data.maxLabel, language: "en" });

  return {
    name: data.name || "rating",
    primaryNodeType: "fmdbext:rating",
    properties,
  };
}

export function getScaleNode(data: ScaleData = {}): JahiaNode {
  const properties = baseProperties(data);
  if (data.minValue !== undefined)
    properties.push({ name: "minValue", value: String(data.minValue), type: "LONG" });
  if (data.maxValue !== undefined)
    properties.push({ name: "maxValue", value: String(data.maxValue), type: "LONG" });
  if (data.step !== undefined)
    properties.push({ name: "step", value: String(data.step), type: "LONG" });
  if (data.minLabel) properties.push({ name: "minLabel", value: data.minLabel, language: "en" });
  if (data.maxLabel) properties.push({ name: "maxLabel", value: data.maxLabel, language: "en" });

  return {
    name: data.name || "scale",
    primaryNodeType: "fmdbext:scale",
    properties,
  };
}

export function getSwitchNode(data: SwitchData = {}): JahiaNode {
  const properties = baseProperties(data);
  if (data.displayMode) properties.push({ name: "displayMode", value: data.displayMode });
  if (data.onLabel) properties.push({ name: "onLabel", value: data.onLabel, language: "en" });
  if (data.offLabel) properties.push({ name: "offLabel", value: data.offLabel, language: "en" });
  if (data.defaultState !== undefined)
    properties.push({ name: "defaultState", value: String(data.defaultState), type: "BOOLEAN" });

  return {
    name: data.name || "switch",
    primaryNodeType: "fmdbext:switch",
    properties,
  };
}

export function getConsentNode(data: ConsentData = {}): JahiaNode {
  const properties = baseProperties(data);
  properties.push({
    name: "statement",
    value: data.statement ?? "<p>I agree to the processing of my personal data.</p>",
    language: "en",
  });
  if (data.termsLinkLabel)
    properties.push({ name: "termsLinkLabel", value: data.termsLinkLabel, language: "en" });

  return {
    name: data.name || "consent",
    primaryNodeType: "fmdbext:consent",
    properties,
  };
}
