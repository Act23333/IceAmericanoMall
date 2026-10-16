package org.icedamericanomall.search.domain.model.search;

/**
 * 笔记向量文档（领域写模型）—— 笔记向量摄取的输入形状（契约 I50，V5.13，ADR-046 管道第二次复用）。
 *
 * <p>与商品向量（{@link SpuVectorDoc}）同构：笔记文本（title + content）是短文本，
 * 单条 embedding 语义已足够，不引入 {@code DocumentChunker}。写入 ES 时整文档覆盖
 * （noteId 幂等键，ES 文档 _id 同源），供 I51 相似检索取自身向量与 kNN 检索共用。
 *
 * @param noteId    笔记 id（幂等键）
 * @param title     笔记标题（向量化文本主来源）
 * @param content   笔记正文（向量化文本，可空）
 * @param embedding 笔记向量（以新向量为准）
 */
public record NoteVectorDoc(Long noteId, String title, String content, double[] embedding) {
}
