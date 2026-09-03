/**
 * 聊天消息，对应上下文里的每一条记录
 */
package frist;
public class ChatMessage {
    // 角色：system / user / assistant / tool
    private String role;
    // 消息内容
    private String content;
    // 工具调用ID（仅tool角色使用，用于关联工具调用和结果）
    private String toolCallId;

    // 构造函数、getter/setter 省略
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
    
    public ChatMessage(String role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
    }
}
