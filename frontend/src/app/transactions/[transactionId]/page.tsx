import { TransactionTraceView } from "@/components/features/transaction-trace-view";
import { WorkspaceGate } from "@/components/layout/workspace-gate";

export default async function TransactionTracePage({
  params,
}: {
  params: Promise<{ transactionId: string }>;
}) {
  const { transactionId } = await params;
  return (
    <WorkspaceGate>
      <TransactionTraceView transactionId={transactionId} />
    </WorkspaceGate>
  );
}
