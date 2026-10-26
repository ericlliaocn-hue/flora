import { parsePromoEndMs } from "@/services/api/studio-features";

/** 活动条截止时刻的本地回退；权威值来自 `GET /api/v1/studio/features`。 */
export const STUDIO_PROMO_END_MS = parsePromoEndMs();
