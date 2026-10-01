package org.icedamericanomall.auth.infrastructure.persistence.userauth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.enums.UserAuthStatusEnum;
import org.icedamericanomall.auth.domain.exception.DuplicateIdentityException;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 认证凭证仓储实现（基础设施层）。
 *
 * <p>所有操作都按<b>业务唯一键</b>（identityType + 盲目索引/明文标识、userId + identityType），
 * 不存在按技术主键 id 的 save / update / delete。
 * 持久化框架的冲突异常在此翻译成领域异常 {@link DuplicateIdentityException}。
 */
@Repository
@RequiredArgsConstructor
public class UserAuthRepositoryImpl implements UserAuthRepository {

    private final UserAuthMapper userAuthMapper;

    @Override
    public UserAuth insert(UserAuth userAuth) {
        UserAuthDO dataObject = UserAuthConverter.toDataObject(userAuth);
        try {
            userAuthMapper.insert(dataObject);
        } catch (DuplicateKeyException e) {
            throw new DuplicateIdentityException(ErrorCode.PARAM_ERROR, "认证标识已存在");
        }
        return UserAuthConverter.toDomain(dataObject);
    }

    @Override
    public int updateByUserIdAndType(UserAuth userAuth) {
        if (userAuth.getUserId() == null || userAuth.getIdentityType() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "更新 UserAuth 必须携带 userId + identityType 业务键");
        }
        UserAuthDO dataObject = UserAuthConverter.toDataObject(userAuth);
        return userAuthMapper.update(dataObject,
                new LambdaUpdateWrapper<UserAuthDO>()
                        .eq(UserAuthDO::getUserId, userAuth.getUserId())
                        .eq(UserAuthDO::getIdentityType, userAuth.getIdentityType().name()));
    }

    @Override
    public UserAuth upsert(UserAuth userAuth) {
        UserAuthDO dataObject = UserAuthConverter.toDataObject(userAuth);
        userAuthMapper.upsert(dataObject);
        return UserAuthConverter.toDomain(dataObject);
    }

    @Override
    public Optional<UserAuth> findByIdentity(IdentityTypeEnum type, String identifierPlain) {
        if (identifierPlain == null) {
            return Optional.empty();
        }
        UserAuthDO dataObject = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuthDO>()
                        .eq(UserAuthDO::getIdentityType, type.name())
                        .eq(UserAuthDO::getIdentifierPlain, identifierPlain)
                        .eq(UserAuthDO::getStatus, UserAuthStatusEnum.ACTIVE.getCode()));
        return Optional.ofNullable(UserAuthConverter.toDomain(dataObject));
    }

    @Override
    public Optional<UserAuth> findByIdentityHash(IdentityTypeEnum type, String identifierHash) {
        UserAuthDO dataObject = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuthDO>()
                        .eq(UserAuthDO::getIdentityType, type.name())
                        .eq(UserAuthDO::getIdentifierHash, identifierHash)
                        .eq(UserAuthDO::getStatus, UserAuthStatusEnum.ACTIVE.getCode()));
        return Optional.ofNullable(UserAuthConverter.toDomain(dataObject));
    }

    @Override
    public Optional<UserAuth> findFirstByUnionId(String unionId) {
        if (unionId == null || unionId.isBlank()) {
            return Optional.empty();
        }
        UserAuthDO dataObject = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuthDO>()
                        .eq(UserAuthDO::getUnionId, unionId)
                        .eq(UserAuthDO::getStatus, UserAuthStatusEnum.ACTIVE.getCode())
                        .last("LIMIT 1"));
        return Optional.ofNullable(UserAuthConverter.toDomain(dataObject));
    }

    @Override
    public List<UserAuth> findByUserId(Long userId) {
        return userAuthMapper.selectList(
                        new LambdaQueryWrapper<UserAuthDO>()
                                .eq(UserAuthDO::getUserId, userId)
                                .eq(UserAuthDO::getStatus, UserAuthStatusEnum.ACTIVE.getCode()))
                .stream()
                .map(UserAuthConverter::toDomain)
                .toList();
    }

    @Override
    public Optional<UserAuth> findByUserIdAndType(Long userId, IdentityTypeEnum type) {
        UserAuthDO dataObject = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuthDO>()
                        .eq(UserAuthDO::getUserId, userId)
                        .eq(UserAuthDO::getIdentityType, type.name())
                        .eq(UserAuthDO::getStatus, UserAuthStatusEnum.ACTIVE.getCode()));
        return Optional.ofNullable(UserAuthConverter.toDomain(dataObject));
    }
}
