package org.icedamericanomall.auth.domain.repository;

import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.common.enums.IdentityTypeEnum;

import java.util.List;
import java.util.Optional;

/**
 * 认证凭证仓储（领域层定义，基础设施层实现）。
 *
 * <p><b>只按业务唯一键操作</b>：
 * <ul>
 *   <li>敏感标识（手机号 / 邮箱）：{@code identityType + identifierHash}（盲索引）</li>
 *   <li>非敏感标识（openid / 用户名）：{@code identityType + identifierPlain}</li>
 *   <li>归属定位：{@code userId + identityType}</li>
 * </ul>
 * 不存在按技术主键 {@code id} 的 save / update / delete 方法。
 * 解绑走状态更新（{@link UserAuth#unbind()}），不做物理删除。
 */
public interface UserAuthRepository {

    /**
     * 新增认证凭证。
     *
     * @param userAuth 领域对象（忽略 {@code id}）
     * @return 落库后的领域对象（含技术主键）
     * @throws org.icedamericanomall.auth.domain.exception.DuplicateIdentityException 业务唯一键冲突
     */
    UserAuth insert(UserAuth userAuth);

    /**
     * 按业务键（userId + identityType）更新凭证（密码、unionId、状态、最后登录时间）。
     *
     * @param userAuth 领域对象，必须携带 userId + identityType
     * @return 受影响行数
     */
    int updateByUserIdAndType(UserAuth userAuth);

    /**
     * 按业务唯一键插入或更新（幂等）。
     *
     * @param userAuth 领域对象
     * @return 落库后的领域对象
     */
    UserAuth upsert(UserAuth userAuth);

    /** 按非敏感标识查询（identityType + 明文标识） */
    Optional<UserAuth> findByIdentity(IdentityTypeEnum type, String identifierPlain);

    /** 按盲索引查询（identityType + HMAC 哈希） */
    Optional<UserAuth> findByIdentityHash(IdentityTypeEnum type, String identifierHash);

    /** 按 UnionID 查询（微信跨应用打通，取第一条） */
    Optional<UserAuth> findFirstByUnionId(String unionId);

    /** 按用户 ID 查询其全部有效认证方式 */
    List<UserAuth> findByUserId(Long userId);

    /** 按用户 ID + 标识类型查询 */
    Optional<UserAuth> findByUserIdAndType(Long userId, IdentityTypeEnum type);
}
