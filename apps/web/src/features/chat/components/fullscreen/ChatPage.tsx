import { useState } from 'react'
import { ChatHistorySidebar } from './ChatHistorySidebar'
import { ChatMainArea } from './ChatMainArea'

export function ChatPage() {
  const [sidebarOpen, setSidebarOpen] = useState(true)

  return (
    <div className="flex h-full">
      {sidebarOpen && (
        <ChatHistorySidebar onClose={() => setSidebarOpen(false)} />
      )}
      <ChatMainArea
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => setSidebarOpen((prev) => !prev)}
      />
    </div>
  )
}
