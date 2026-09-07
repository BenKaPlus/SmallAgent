package util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实天气查询工具
 * 数据源：免费天气 API（t.weather.itboy.net），内置城市名 → 城市ID 映射
 * 用户只需询问城市，LLM 传城市名，本工具完成映射与查询
 */
public class WeatherTool implements Tool {
    private static final String API_BASE = "http://t.weather.itboy.net/api/weather/city/";

    // 城市名 → 城市ID 映射表（LinkedHashMap 保证支持城市列表顺序稳定）
    private static final Map<String, String> CITY_CODES = new LinkedHashMap<>();

    static {
        CITY_CODES.put("北京", "101010100");
        CITY_CODES.put("天津", "101030100");
        CITY_CODES.put("上海", "101020100");
        CITY_CODES.put("重庆", "101040100");
        CITY_CODES.put("广州", "101280101");
        CITY_CODES.put("深圳", "101280601");
        CITY_CODES.put("石家庄", "101090101");
        CITY_CODES.put("郑州", "101180101");
        CITY_CODES.put("武汉", "101200101");
        CITY_CODES.put("长沙", "101250101");
        CITY_CODES.put("南京", "101190101");
        CITY_CODES.put("杭州", "101210101");
        CITY_CODES.put("成都", "101270101");
        CITY_CODES.put("西安", "101110101");
        CITY_CODES.put("沈阳", "101070101");
        CITY_CODES.put("长春", "101060101");
        CITY_CODES.put("哈尔滨", "101050101");
        CITY_CODES.put("太原", "101100101");
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WeatherTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "weather_query";
    }

    @Override
    public String getDescription() {
        return "查询城市的真实天气（当前温度、湿度、空气质量、今明两天预报）。"
                + "当用户询问天气、温度、是否下雨时，优先使用本工具而不是搜索。"
                + "当前支持18个城市：" + String.join("、", CITY_CODES.keySet());
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> city = new HashMap<>();
        city.put("type", "string");
        city.put("description", "城市名，例如：深圳、北京");
        properties.put("city", city);

        schema.put("properties", properties);
        schema.put("required", List.of("city"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) throws Exception {
        Object cityObj = params.get("city");
        String city = cityObj == null ? "" : cityObj.toString().trim();
        if (city.isEmpty()) {
            return "查询失败：请提供城市名";
        }

        // 城市名 → 城市ID 映射，不支持的城市提前返回，不发 HTTP 请求
        String cityId = resolveCityId(city);
        if (cityId == null) {
            return "暂不支持城市「" + city + "」，当前支持：" + String.join("、", CITY_CODES.keySet());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + cityId))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return "天气查询失败，状态码：" + response.statusCode();
        }
        return formatWeather(response.body());
    }

    /**
     * 城市名 → 城市ID，兼容「深圳」「深圳市」两种写法
     * 包级可见，便于单元测试
     */
    static String resolveCityId(String cityName) {
        String normalized = cityName.trim();
        if (normalized.endsWith("市") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return CITY_CODES.get(normalized);
    }

    /**
     * 解析 API 响应为可读文本
     * 独立成方法便于单元测试（不依赖网络）
     */
    String formatWeather(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            JsonNode forecast = data.path("forecast");
            if (!data.isObject() || !forecast.isArray() || forecast.size() == 0) {
                return "天气数据解析失败：返回内容不完整";
            }

            String cityName = root.path("cityInfo").path("city").asText("未知城市");
            JsonNode today = forecast.get(0);

            StringBuilder sb = new StringBuilder();
            sb.append(cityName).append("今天天气：");
            sb.append(today.path("type").asText("未知")).append("，");
            sb.append("当前温度 ").append(data.path("wendu").asText("?")).append("℃，");
            sb.append(today.path("high").asText("")).append("，").append(today.path("low").asText("")).append("，");
            sb.append(today.path("fx").asText("")).append(" ").append(today.path("fl").asText("")).append("，");
            sb.append("湿度 ").append(data.path("shidu").asText("?"));
            if (!data.path("quality").isMissingNode()) {
                sb.append("，空气质量 ").append(data.path("quality").asText());
            }
            if (forecast.size() > 1) {
                JsonNode tomorrow = forecast.get(1);
                sb.append("。明天：").append(tomorrow.path("type").asText("未知")).append("，");
                sb.append(tomorrow.path("high").asText("")).append("，").append(tomorrow.path("low").asText(""));
            }
            return sb.toString();
        } catch (Exception e) {
            return "天气数据解析失败：" + e.getMessage();
        }
    }
}
