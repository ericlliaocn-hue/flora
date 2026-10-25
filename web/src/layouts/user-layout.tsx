import type { ReactNode } from "react";

import { AgentPanel } from "@/components/agent/agent-panel";
import { AuthModal } from "@/components/auth/auth-modal";
import { StudioShell } from "@/components/layout/studio/studio-shell";

export default function UserLayout({ children }: { children: ReactNode }) {
    return (
        <div className="flex h-dvh overflow-hidden bg-[#0b0b0b] text-neutral-100">
            <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
                <StudioShell>{children}</StudioShell>
            </div>
            <AgentPanel />
            <AuthModal />
        </div>
    );
}
