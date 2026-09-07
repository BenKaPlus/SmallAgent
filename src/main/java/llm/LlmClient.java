package llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import first.ChatMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmClient {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用聊天补全接口
     * @param messages 上下文消息列表
     * @param tools 工具 Schema 列表，为空则不启用工具调用
     * @return 响应 Map，包含内容、工具调用等
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chatCompletion(List<ChatMessage> messages, List<Map<String, Object>> tools) throws Exception {
        // 构建请求体
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.1
        );

        // 如果有工具，加入 tools 参数
        if (tools != null && !tools.isEmpty()) {
            requestBody = new HashMap<>(requestBody);
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto");
        }

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM 调用失败，状态码：" + response.statusCode() + "，内容：" + response.body());
        }

        Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
        // 提取第一条选择的消息
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        return (Map<String, Object>) choices.get(0).get("message");
    }
}
