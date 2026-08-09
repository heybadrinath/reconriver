import { RunDetails } from "@/components/features/run-details";
import { WorkspaceGate } from "@/components/layout/workspace-gate";

export default async function RunDetailsPage({
  params,
}: {
  params: Promise<{ runId: string }>;
}) {
  const { runId } = await params;
  return (
    <WorkspaceGate>
      <RunDetails runId={runId} />
    </WorkspaceGate>
  );
}
