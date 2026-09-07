package util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WeatherTool 单元测试
 * 全部用例不依赖网络：城市映射与 JSON 解析逻辑独立测试
 */
class WeatherToolTest {

    private final WeatherTool tool = new WeatherTool();

    @Test
    void nameAndDescriptionShouldBeStable() {
        assertEquals("weather_query", tool.getName());
        assertTrue(tool.getDescription().contains("天气"));
        // 描述里应列出支持的城市，供 LLM 判断哪些城市可用本工具
        assertTrue(tool.getDescription().contains("深圳"));
    }

    @Test
    void shouldResolvePlainAndSuffixedCityName() {
        // 「深圳」与「深圳市」应映射到同一 ID
        assertEquals("101280601", WeatherTool.resolveCityId("深圳"));
        assertEquals("101280601", WeatherTool.resolveCityId("深圳市"));
        assertEquals("101010100", WeatherTool.resolveCityId("北京"));
        assertEquals("101280101", WeatherTool.resolveCityId("广州市"));
    }

    @Test
    void shouldReturnNullForUnsupportedCity() {
        assertNull(WeatherTool.resolveCityId("火星"));
        assertNull(WeatherTool.resolveCityId("巴黎"));
    }

    @Test
    void shouldRejectEmptyCityOnExecute() throws Exception {
        String result = tool.execute(Map.of());
        assertTrue(result.startsWith("查询失败"), "空城市名应提前返回，实际：" + result);
    }

    @Test
    void shouldReturnUnsupportedMessageBeforeHttp() throws Exception {
        // 不支持的城市应在发 HTTP 请求前返回友好提示
        String result = tool.execute(Map.of("city", "火星市"));
        assertTrue(result.startsWith("暂不支持"), "不支持城市应提前返回，实际：" + result);
        assertTrue(result.contains("当前支持"), "提示里应列出支持的城市列表，实际：" + result);
    }

    @Test
    void shouldParseSampleApiResponse() {
        // 用真实 API 结构构造的样例响应，验证解析逻辑（不依赖网络）
        String json = "{\"cityInfo\":{\"city\":\"深圳市\"},\"data\":{\"wendu\":\"32.9\",\"shidu\":\"79%\","
                + "\"quality\":\"优\",\"forecast\":["
                + "{\"type\":\"小雨\",\"high\":\"高温 32℃\",\"low\":\"低温 26℃\",\"fx\":\"南风\",\"fl\":\"1级\"},"
                + "{\"type\":\"晴\",\"high\":\"高温 35℃\",\"low\":\"低温 27℃\"}]}}";
        String result = tool.formatWeather(json);

        assertTrue(result.contains("深圳市"), "应包含城市名，实际：" + result);
        assertTrue(result.contains("小雨"), "应包含今天天气类型");
        assertTrue(result.contains("32.9"), "应包含当前温度");
        assertTrue(result.contains("高温 32℃"), "应包含今天最高温");
        assertTrue(result.contains("湿度 79%"), "应包含湿度");
        assertTrue(result.contains("空气质量 优"), "应包含空气质量");
        assertTrue(result.contains("明天：晴"), "应包含明天预报");
    }

    @Test
    void shouldReturnParseErrorOnBrokenResponse() {
        String result = tool.formatWeather("{\"message\":\"error\"}");
        assertTrue(result.startsWith("天气数据解析失败"), "缺 data/forecast 的响应应返回解析失败，实际：" + result);
    }

    @Test
    void schemaShouldRequireCityField() {
        Map<String, Object> schema = tool.getParametersSchema();
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertTrue(required.contains("city"));
    }
}
