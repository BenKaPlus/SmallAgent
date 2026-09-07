package util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockSearchTool implements Tool {
    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "网络搜索工具，用于查询新闻、常识、科普等通用信息。天气查询不要用本工具，请使用 weather_query";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> query = new HashMap<>();
        query.put("type", "string");
        query.put("description", "搜索关键词");
        properties.put("query", query);
        
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        // Mock 结果，实际可以接入真实搜索 API
        return "【搜索结果】关于「" + query + "」的信息：\n" +
                "1. 这是一条模拟搜索结果，当前时间：" + java.time.LocalDateTime.now();
    }
}
