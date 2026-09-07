package llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import first.ChatMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmClient {
    // 最大重试次数（含首次）
    private static final int MAX_RETRIES = 3;
    // 重试退避基数（毫秒），实际等待 = 基数 * 当前尝试次数
    private static final long RETRY_BACKOFF_MILLIS = 1000L;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    // 非标准 OpenAI 参数，以顶层字段合并进请求体
    // 例：百炼的 enable_thinking=false 可关闭 qwen3.8-max 的思考模式，避免 Agent 反复调工具不收敛
    private Map<String, Object> extraBody;

    public LlmClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void setExtraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody;
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

        // 合并非标准参数（如百炼的 enable_thinking），原样以顶层字段发出
        if (extraBody != null && !extraBody.isEmpty()) {
            requestBody = new HashMap<>(requestBody);
            requestBody.putAll(extraBody);
        }

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        // 对网络异常与 429/5xx 做指数退避重试
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if ((code == 429 || code >= 500) && attempt < MAX_RETRIES) {
                    System.err.println("[LLM Warn] 状态码 " + code + "，第 " + attempt + " 次重试...");
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
                    continue;
                }
                if (code != 200) {
                    throw new RuntimeException("LLM 调用失败，状态码：" + code + "，内容：" + response.body());
                }

                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                // 提取第一条选择的消息
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                return (Map<String, Object>) choices.get(0).get("message");
            } catch (java.io.IOException e) {
                lastError = e;
                if (attempt < MAX_RETRIES) {
                    System.err.println("[LLM Warn] 网络异常 " + e.getMessage() + "，第 " + attempt + " 次重试...");
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
                }
            }
            // InterruptedException 直接向上抛出，终止重试
        }
        throw new RuntimeException("LLM 调用重试耗尽", lastError);
    }
}
