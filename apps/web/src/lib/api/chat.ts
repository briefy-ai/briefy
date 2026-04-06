import { apiDelete, apiGet, apiPostEventStream } from './client'
import type {
  ChatConversationPageResponse,
  ChatConversationResponse,
  ChatStreamEvent,
  SendChatMessageRequest,
} from './types'

interface ListChatConversationsParams {
  limit?: number
  cursor?: string
}

export async function streamConversationMessage(
  conversationId: string,
  request: SendChatMessageRequest,
  onEvent: (event: ChatStreamEvent) => boolean | void,
  signal?: AbortSignal
): Promise<void> {
  const path = conversationId === 'new'
    ? '/api/chat/conversations/new/messages'
    : `/api/chat/conversations/${conversationId}/messages`

  await apiPostEventStream<ChatStreamEvent>(path, request, onEvent, signal)
}

export async function listChatConversations(
  params: ListChatConversationsParams = {}
): Promise<ChatConversationPageResponse> {
  const searchParams = new URLSearchParams()
  if (params.limit) searchParams.set('limit', String(params.limit))
  if (params.cursor) searchParams.set('cursor', params.cursor)
  const query = searchParams.toString()

  return apiGet<ChatConversationPageResponse>(
    query ? `/api/chat/conversations?${query}` : '/api/chat/conversations'
  )
}

export async function getChatConversation(id: string): Promise<ChatConversationResponse> {
  return apiGet<ChatConversationResponse>(`/api/chat/conversations/${id}`)
}

export async function deleteChatConversation(id: string): Promise<void> {
  await apiDelete(`/api/chat/conversations/${id}`)
}
