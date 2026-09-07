package util;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CalculatorTool 单元测试
 * 覆盖：基础运算、Math.pow 幂运算、非法表达式容错、schema 规范
 */
class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();

    @Test
    void nameAndDescriptionShouldBeStable() {
        assertEquals("calculator", tool.getName());
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isBlank());
    }

    @Test
    void shouldCalculateBasicArithmetic() throws Exception {
        Map<String, Object> params = Map.of("expression", "1 + 2 * 3");
        String result = tool.execute(params);
        assertTrue(result.contains("7"), "1+2*3 应等于 7，实际：" + result);
    }

    @Test
    void shouldSupportPowerViaMathPow() throws Exception {
        // 文档明确：^ 在 JS 中是位异或，幂运算须用 Math.pow(a, b)
        Map<String, Object> params = Map.of("expression", "Math.pow(2, 10)");
        String result = tool.execute(params);
        assertTrue(result.contains("1024"), "2^10 应等于 1024，实际：" + result);
    }

    @Test
    void shouldReturnErrorOnInvalidExpression() throws Exception {
        // 语法错误的表达式（未闭合括号），Nashorn 必然抛错
        Map<String, Object> params = Map.of("expression", "(1 + 2");
        String result = tool.execute(params);
        assertTrue(result.startsWith("计算失败"), "非法表达式应返回失败提示，实际：" + result);
    }

    @Test
    void schemaShouldContainExpressionAsRequired() {
        Map<String, Object> schema = tool.getParametersSchema();
        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties.get("expression"));
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertTrue(required.contains("expression"));
    }
}
