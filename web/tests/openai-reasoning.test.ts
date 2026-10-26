import { expect, test } from "bun:test";

import { buildOpenAiReasoning, isLunaProModel } from "../src/lib/openai-reasoning";

test("detects Luna Pro model ids", () => {
    expect(isLunaProModel("gpt-5.6-luna-pro")).toBe(true);
    expect(isLunaProModel("openai/gpt-5.6-luna-pro")).toBe(true);
    expect(isLunaProModel("gpt-image-2")).toBe(false);
});

test("Luna Pro sends reasoning.mode pro with default effort when auto", () => {
    expect(buildOpenAiReasoning("gpt-5.6-luna-pro", "auto")).toEqual({ mode: "pro", effort: "medium" });
});

test("non-Luna models omit reasoning when effort is auto", () => {
    expect(buildOpenAiReasoning("gpt-5.5", "auto")).toBeUndefined();
});

test("non-Luna models only send effort when configured", () => {
    expect(buildOpenAiReasoning("gpt-5.5", "high")).toEqual({ effort: "high" });
});

test("Luna Pro keeps explicit effort", () => {
    expect(buildOpenAiReasoning("gpt-5.6-luna-pro", "high")).toEqual({ mode: "pro", effort: "high" });
});
