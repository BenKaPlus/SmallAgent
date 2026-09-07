package util;

import java.util.HashMap;
import java.util.List;
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
        expression.put("description", "数学表达式，例如：3.14 * 5 + sqrt(16)；幂运算请用 Math.pow(a, b)，不要用 ^");
        properties.put("expression", expression);

        schema.put("properties", properties);
        schema.put("required", List.of("expression"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) throws Exception {
        String expression = (String) params.get("expression");
        // 用 Java 脚本引擎计算表达式。注意：JavaScript 中 ^ 是位异或，幂运算须用 Math.pow(a, b)
        try {
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager().getEngineByName("js");
            if (engine == null) {
                return "计算失败：当前 JDK 未提供脚本引擎（建议使用 Math.pow 语法或更换实现）";
            }
            Object result = engine.eval(expression);
            return "计算结果：" + result;
        } catch (Exception e) {
            return "计算失败：表达式格式错误 - " + e.getMessage();
        }
    }
}
