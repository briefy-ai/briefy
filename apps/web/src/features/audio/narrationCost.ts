const COST_CONFIRMATION_CHAR_THRESHOLD = 6_000

export function formatCostDisplay(amountUsd: number): string {
  if (amountUsd <= 0) return '~$0.00'
  if (amountUsd < 0.01) return `~$${amountUsd.toFixed(3)}`
  return `~$${amountUsd.toFixed(2)}`
}

export function shouldConfirmCost(charCount: number): boolean {
  return charCount > COST_CONFIRMATION_CHAR_THRESHOLD
}
