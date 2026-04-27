package com.briefy.api.infrastructure.mcp

import com.briefy.api.infrastructure.mcp.tools.GetBriefingTool
import com.briefy.api.infrastructure.mcp.tools.GetByTopicTool
import com.briefy.api.infrastructure.mcp.tools.GetSourceTool
import com.briefy.api.infrastructure.mcp.tools.GetTopicsTool
import com.briefy.api.infrastructure.mcp.tools.SearchBriefingsTool
import com.briefy.api.infrastructure.mcp.tools.SearchRelatedTool
import com.briefy.api.infrastructure.mcp.tools.SearchSourcesTool
import org.springframework.ai.tool.ToolCallback
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpToolsConfig {

    @Bean
    fun briefyMcpToolCallbacks(
        searchSources: SearchSourcesTool,
        getSource: GetSourceTool,
        searchBriefings: SearchBriefingsTool,
        getBriefing: GetBriefingTool,
        getTopics: GetTopicsTool,
        getByTopic: GetByTopicTool,
        searchRelated: SearchRelatedTool,
    ): List<ToolCallback> = listOf(
        searchSources.toolCallback(),
        getSource.toolCallback(),
        searchBriefings.toolCallback(),
        getBriefing.toolCallback(),
        getTopics.toolCallback(),
        getByTopic.toolCallback(),
        searchRelated.toolCallback(),
    )
}
