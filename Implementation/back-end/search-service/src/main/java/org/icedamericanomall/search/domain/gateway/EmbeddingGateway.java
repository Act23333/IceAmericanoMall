package org.icedamericanomall.search.domain.gateway;

/**
 * Embedding 网关（领域端口）—— 文本向量化能力抽象（OpenAI 兼容协议）。
 *
 * <p>实现方：{@code infrastructure.adapter.HttpEmbeddingGateway}。
 * 失败语义 fail-loud：{@link #embed(String)} 失败抛业务异常，由调用方（app 编排）降级。
 */
public interface EmbeddingGateway {

    /** 是否已具备调用条件（enabled + baseUrl + apiKey 齐备）。 */
    boolean isConfigured();

    /**
     * 文本向量化。
     *
     * @param text 输入文本（chunk 正文）
     * @return 定长向量（长度 = 配置 dims）
     */
    float[] embed(String text);
}
