package util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalculatorTool implements Tool {
    // 允许使用的安全函数/常量（白名单），其他字母组合一律拒绝，防止脚本注入
    // 例：java.lang.Runtime.getRuntime().exec(...) 会被白名单拦截
    private static final String[] ALLOWED_TOKENS = {"Math.pow", "Math.sqrt", "Math.PI", "Math.E", "sqrt"};

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
        if (expression == null || expression.isBlank()) {
            return "计算失败：表达式为空";
        }

        // 安全过滤：先把白名单函数名替换为占位符，剩余部分只允许数字和运算符
        // 这样可以阻止 java.lang.Runtime 等危险调用
        String safe = expression;
        for (String token : ALLOWED_TOKENS) {
            safe = safe.replace(token, "");
        }
        // 剩余字符只允许：数字、小数点、四则运算符、括号、逗号、空格
        if (!safe.matches("^[0-9+\\-*/().\\s,]+$")) {
            return "计算失败：表达式包含非法字符，仅支持数字、+-*/() 和 Math.pow/sqrt";
        }

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
