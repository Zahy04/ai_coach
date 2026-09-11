package cz.rzahr.aicoach.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GenerateContentRequest(
    val systemInstruction: Content? = null,
    val contents: List<Content>,
    val tools: List<Tools>? = null,
    val toolConfig: ToolConfig? = null,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null,
    val inlineData: InlineData? = null,
    val thoughtSignature: String? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class ModelsResponse(
    val models: List<ModelInfo> = emptyList()
)

@Serializable
data class ModelInfo(
    val name: String = "",
    val supportedGenerationMethods: List<String> = emptyList()
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: JsonObject? = null
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject
)

@Serializable
data class Tools(
    val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class ToolConfig(
    val functionCallingConfig: FunctionCallingConfig
)

@Serializable
data class FunctionCallingConfig(
    val mode: String
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

@Serializable
data class ApiError(
    val error: ApiErrorInfo? = null
)

@Serializable
data class ApiErrorInfo(
    val message: String? = null
)
