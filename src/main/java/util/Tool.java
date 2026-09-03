package util;

import java.util.Map;

/**
 * 工具标准接口，所有工具必须实现
 */
public interface Tool {
    // 工具名称，必须唯一
    String getName();
    
    // 工具功能描述，给 LLM 看的，描述越准确调用越准
    String getDescription();
    
    // 参数 Schema：定义参数名、类型、是否必填、描述
    // 格式和 OpenAI Function Call 格式对齐
    Map<String, Object> getParametersSchema();
    
    // 执行工具，传入参数 Map，返回结果字符串
    String execute(Map<String, Object> params) throws Exception;
}
