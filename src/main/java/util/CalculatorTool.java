package util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计算器工具
 * 使用自研递归下降求值器解析数学表达式，不依赖 javax.script（Nashorn）：
 * 1. JDK 11 起 Nashorn 标记弃用会打警告，JDK 15+ 已移除（getEngineByName 返回 null）
 * 2. 不再 eval 任意字符串，从根上消除脚本注入面（白名单校验保留作为前置防线）
 */
public class CalculatorTool implements Tool {
    // 允许使用的安全函数/常量（白名单），其他字母组合一律拒绝，防提示注入
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
    public String execute(Map<String, Object> params) {
        String expression = (String) params.get("expression");
        if (expression == null || expression.isBlank()) {
            return "计算失败：表达式为空";
        }

        // 前置白名单校验：先把允许的函数名替换掉，剩余部分只允许数字和运算符
        String safe = expression;
        for (String token : ALLOWED_TOKENS) {
            safe = safe.replace(token, "");
        }
        if (!safe.matches("^[0-9+\\-*/().\\s,]+$")) {
            return "计算失败：表达式包含非法字符，仅支持数字、+-*/() 和 Math.pow/sqrt";
        }

        try {
            double result = new ExpressionEvaluator(expression).parse();
            return "计算结果：" + formatNumber(result);
        } catch (Exception e) {
            return "计算失败：表达式格式错误 - " + e.getMessage();
        }
    }

    /**
     * 整数不带小数点输出；小数按 12 位有效精度去除浮点噪声（如 19.700000000000003 → 19.7）
     */
    static String formatNumber(double v) {
        if (!Double.isNaN(v) && !Double.isInfinite(v) && v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return String.valueOf(v);
        }
        return new BigDecimal(v).round(new MathContext(12))
                .stripTrailingZeros().toPlainString();
    }

    /**
     * 递归下降表达式求值器
     * 文法：expression := term (('+'|'-') term)*
     *       term       := factor (('*'|'/') factor)*
     *       factor     := number | '(' expression ')' | '-' factor | func '(' args ')'
     */
    private static final class ExpressionEvaluator {
        private final String src;
        private int pos;

        ExpressionEvaluator(String src) {
            this.src = src;
        }

        double parse() {
            double v = parseExpression();
            skipWhitespace();
            if (pos != src.length()) {
                throw new IllegalArgumentException("存在无法解析的字符（位置 " + pos + "）");
            }
            return v;
        }

        private double parseExpression() {
            double v = parseTerm();
            while (true) {
                skipWhitespace();
                if (peek('+')) {
                    pos++;
                    v += parseTerm();
                } else if (peek('-')) {
                    pos++;
                    v -= parseTerm();
                } else {
                    return v;
                }
            }
        }

        private double parseTerm() {
            double v = parseFactor();
            while (true) {
                skipWhitespace();
                if (peek('*')) {
                    pos++;
                    v *= parseFactor();
                } else if (peek('/')) {
                    pos++;
                    v /= parseFactor();
                } else {
                    return v;
                }
            }
        }

        private double parseFactor() {
            skipWhitespace();
            if (peek('-')) {          // 一元负号，支持 2 * -3
                pos++;
                return -parseFactor();
            }
            if (peek('+')) {
                pos++;
                return parseFactor();
            }
            if (peek('(')) {
                pos++;
                double v = parseExpression();
                expect(')');
                return v;
            }
            if (Character.isLetter(lookahead())) {
                return parseFunctionOrConstant();
            }
            return parseNumber();
        }

        private double parseFunctionOrConstant() {
            int start = pos;
            while (pos < src.length()
                    && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            String name = src.substring(start, pos);
            switch (name) {
                case "Math.PI":
                    return Math.PI;
                case "Math.E":
                    return Math.E;
                case "Math.pow":
                    expect('(');
                    double a = parseExpression();
                    expect(',');
                    double b = parseExpression();
                    expect(')');
                    return Math.pow(a, b);
                case "Math.sqrt":
                case "sqrt":
                    expect('(');
                    double x = parseExpression();
                    expect(')');
                    return Math.sqrt(x);
                default:
                    throw new IllegalArgumentException("不支持的函数或常量：" + name);
            }
        }

        private double parseNumber() {
            int start = pos;
            while (pos < src.length()
                    && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("缺少操作数（位置 " + pos + "）");
            }
            try {
                return Double.parseDouble(src.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("非法数字：" + src.substring(start, pos));
            }
        }

        private boolean peek(char c) {
            return pos < src.length() && src.charAt(pos) == c;
        }

        private char lookahead() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private void expect(char c) {
            skipWhitespace();
            if (!peek(c)) {
                throw new IllegalArgumentException("期望 '" + c + "'（位置 " + pos + "）");
            }
            pos++;
        }
    }
}
