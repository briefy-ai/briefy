import type { ComponentType } from 'react'
import type { ChatMessage, ChatMessageType } from '../types'
import { useChatMessageHandlers } from './ChatMessageHandlers'
import { UnknownMessageFallback } from './UnknownMessageFallback'
import type { MessageComponentProps } from './messageTypes'
import { BriefingResultMessage } from './messages/BriefingResultMessage'
import { ErrorMessage } from './messages/ErrorMessage'
import { IntentSelectorMessage } from './messages/IntentSelectorMessage'
import { PlanPreviewMessage } from './messages/PlanPreviewMessage'
import { StepProgressMessage } from './messages/StepProgressMessage'
import { SystemTextMessage } from './messages/SystemTextMessage'
import { UserActionMessage } from './messages/UserActionMessage'
import { UserTextMessage } from './messages/UserTextMessage'

type RendererComponent = ComponentType<MessageComponentProps<ChatMessage>>

const messageRegistry: Partial<Record<ChatMessageType, RendererComponent>> = {
  system_text: SystemTextMessage as RendererComponent,
  user_text: UserTextMessage as RendererComponent,
  user_action: UserActionMessage as RendererComponent,
  intent_selector: IntentSelectorMessage as RendererComponent,
  plan_preview: PlanPreviewMessage as RendererComponent,
  step_progress: StepProgressMessage as RendererComponent,
  briefing_result: BriefingResultMessage as RendererComponent,
  error: ErrorMessage as RendererComponent,
}

export function MessageRenderer({ message }: { message: ChatMessage }) {
  const handlers = useChatMessageHandlers()
  const Component = messageRegistry[message.type]

  if (!Component) {
    return <UnknownMessageFallback message={message} />
  }

  return <Component message={message} {...handlers} />
}
