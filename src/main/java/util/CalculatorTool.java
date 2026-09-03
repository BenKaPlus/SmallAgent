package util;

import java.util.HashMap;
import java.util.Map;

public class CalculatorTool implements Tool {
    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "专业数学计算器，支持加减乘除、幂运算、开方等数学计算，仅接受数学表达式";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> expression = new HashMap<>();
        expression.put("type", "string");
        expression.put("description", "数学表达式，例如：3.14 * 5^2 + sqrt(16)");
        properties.put("expression", expression);
        
        schema.put("properties", properties);
        schema.put("required", List.of("expression"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) throws Exception {
        String expression = (String) params.get("expression");
        // 简单实现，用 Java 脚本引擎计算表达式
        try {
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager().getEngineByName("js");
            Object result = engine.eval(expression.replace("^", "**"));
            return "计算结果：" + result;
        } catch (Exception e) {
            return "计算失败：表达式格式错误 - " + e.getMessage();
        }
    }
}
