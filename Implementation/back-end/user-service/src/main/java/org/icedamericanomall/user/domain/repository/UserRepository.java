package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.user.domain.exception.DuplicateUserException;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.model.UserStatusEnum;

import java.util.List;
import java.util.Optional;

/**
 * 用户聚合仓储（领域层定义，基础设施层实现）。
 *
 * <p>业务键口径（ADR-002 / ADR-006）：
 * <ul>
 *   <li>创建：{@code insert}（由实现生成 {@code userId} UUID 业务键，不做"按 id 判断增减"）</li>
 *   <li>登录定位：{@code findByPhone} / {@code findByUsername} / {@code findByWxOpenid}（业务列）</li>
 *   <li>聚合定位：{@code findById} —— 技术主键是集群内部口径（JWT / 网关 header / 各业务表 user_id），
 *       聚合一旦装载，其后的变更（资料/状态/余额）按 id 定位是该聚合的规范操作，非"无谓按 id"</li>
 * </ul>
 */
public interface UserRepository {

    /** 按技术主键查询（跨服务口径，ADR-006）。 */
    Optional<User> findById(Long id);

    /** 按登录标识查询（当前仅 PHONE 落 user 表列；EMAIL / WECHAT 由 user_auth 承载）。 */
    Optional<User> findByIdentity(IdentityTypeEnum identityType, String identifier);

    /** 按手机号查询（uk_phone 业务唯一键）。 */
    Optional<User> findByPhone(String phone);

    /** 按用户名查询（登录用）。 */
    Optional<User> findByUsername(String username);

    /** 按微信 openid 查询（登录用）。 */
    Optional<User> findByWxOpenid(String wxOpenid);

    /**
     * 新增用户（生成 user_id 业务键与默认状态，回填技术主键）。
     *
     * @param user 领域对象（忽略 {@code id} / {@code userId}）
     * @return 落库后的领域对象（含 id + userId）
     * @throws DuplicateUserException 业务唯一键冲突（并发创建由上层回查兜底）
     */
    User insert(User user);

    /** 全量更新（资料编辑：昵称/头像等；@Version 乐观锁生效，entity 须已装载）。 */
    int update(User user);

    /** 管理后台：启用/禁用状态迁移。 */
    int updateStatusById(Long id, UserStatusEnum status);

    /** 管理后台：设置角色类型。 */
    int updateRoleTypeById(Long id, int roleType);

    /**
     * 原子条件扣减余额（余额支付）：仅当余额充足才更新。
     *
     * @return 受影响行数（0 = 余额不足或用户不存在）
     */
    int deductBalance(Long id, int amount);

    /** 原子增加余额（充值/退款）。 */
    int creditBalance(Long id, int amount);

    /** 用户总数（管理后台 / 跨服务统计）。 */
    long countAll();

    /**
     * 按注册时间倒序分页（管理后台读模型；不引入分页框架类型，由应用层组装响应）。
     *
     * @param offset 偏移量（(pageNum-1) * pageSize）
     * @param size   单页条数
     */
    List<User> findPageDescByCreateTime(int offset, int size);
}
