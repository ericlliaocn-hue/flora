import type { ReasoningEffort } from "@/stores/use-config-store";

/** Luna Pro 在 pro 号池等网关需显式 {@code reasoning.mode = "pro"}，与 effort 无关。 */
export function isLunaProModel(model: string) {
    const name = model.trim().toLowerCase();
    return name.includes("luna-pro") || name.includes("luna_pro") || /luna[^a-z0-9]*pro/.test(name);
}

/** 组装 OpenAI Responses API 的 {@code reasoning} 字段；无参数时返回 undefined。 */
export function buildOpenAiReasoning(model: string, effort: ReasoningEffort): { mode?: string; effort?: string } | undefined {
    const needsProMode = isLunaProModel(model);
    const effortValue = effort !== "auto" ? effort : needsProMode ? "medium" : undefined;
    if (!needsProMode && !effortValue) return undefined;
    return {
        ...(needsProMode ? { mode: "pro" } : {}),
        ...(effortValue ? { effort: effortValue } : {}),
    };
}
