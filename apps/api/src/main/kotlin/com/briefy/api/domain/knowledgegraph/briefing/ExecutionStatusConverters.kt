package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class BriefingRunStatusConverter : AttributeConverter<BriefingRunStatus, String> {
    override fun convertToDatabaseColumn(attribute: BriefingRunStatus?): String? {
        return attribute?.dbValue
    }

    override fun convertToEntityAttribute(dbData: String?): BriefingRunStatus? {
        return dbData?.let { BriefingRunStatus.fromDbValue(it) }
    }
}

@Converter
class SubagentRunStatusConverter : AttributeConverter<SubagentRunStatus, String> {
    override fun convertToDatabaseColumn(attribute: SubagentRunStatus?): String? {
        return attribute?.dbValue
    }

    override fun convertToEntityAttribute(dbData: String?): SubagentRunStatus? {
        return dbData?.let { SubagentRunStatus.fromDbValue(it) }
    }
}

@Converter
class SynthesisRunStatusConverter : AttributeConverter<SynthesisRunStatus, String> {
    override fun convertToDatabaseColumn(attribute: SynthesisRunStatus?): String? {
        return attribute?.dbValue
    }

    override fun convertToEntityAttribute(dbData: String?): SynthesisRunStatus? {
        return dbData?.let { SynthesisRunStatus.fromDbValue(it) }
    }
}

@Converter
class BriefingRunFailureCodeConverter : AttributeConverter<BriefingRunFailureCode, String> {
    override fun convertToDatabaseColumn(attribute: BriefingRunFailureCode?): String? {
        return attribute?.dbValue
    }

    override fun convertToEntityAttribute(dbData: String?): BriefingRunFailureCode? {
        return dbData?.let { BriefingRunFailureCode.fromDbValue(it) }
    }
}
