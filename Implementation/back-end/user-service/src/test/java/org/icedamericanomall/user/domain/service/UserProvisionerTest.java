package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.user.domain.exception.DuplicateUserException;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.port.DistributedLock;
import org.icedamericanomall.user.domain.port.NewUserRegistrar;
import org.icedamericanomall.user.domain.port.NewUserRewardService;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户"查找或创建"领域服务单元测试 —— 幂等三层防线（先查短路 / 锁内双重检查 / 冲突回查）
 * 与 V5.2 建档漏斗（昵称透传 + 建档奖励仅新建分支）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserProvisioner 领域单元测试")
class UserProvisionerTest {

    @Mock UserRepository userRepository;
    @Mock DistributedLock distributedLock;
    @Mock NewUserRegistrar newUserRegistrar;
    @Mock NewUserRewardService newUserRewardService;

    private UserProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new UserProvisioner(userRepository, distributedLock, newUserRegistrar, newUserRewardService);
    }

    @Test
    @DisplayName("findOrCreate — 已存在：直接返回，不加锁不建档不发奖励")
    void shouldReturnExisting_withoutLock() {
        User existing = User.builder().id(7L).userId("u-7").nickname("老用户").build();
        when(userRepository.findByIdentity(IdentityTypeEnum.PHONE, "13800138000"))
                .thenReturn(Optional.of(existing));

        User result = provisioner.findOrCreate(IdentityTypeEnum.PHONE, "13800138000", "新昵称");

        assertEquals(7L, result.getId());
        verify(distributedLock, never()).executeWithLock(anyString(), any());
        verify(newUserRegistrar, never()).register(any());
        verify(newUserRewardService, never()).award(any());
    }

    @Test
    @DisplayName("findOrCreate — 不存在：锁内双重检查（第二次仍未命中）后建档 + 发建房奖励")
    void shouldCreate_whenAbsent() {
        User created = User.builder().id(9L).userId("u-9").build();
        when(userRepository.findByIdentity(IdentityTypeEnum.PHONE, "13800138000"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(newUserRegistrar.register(any())).thenReturn(created);
        // 锁端口直通临界区
        when(distributedLock.executeWithLock(anyString(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        User result = provisioner.findOrCreate(IdentityTypeEnum.PHONE, "13800138000", "新用户");

        assertEquals(9L, result.getId());
        verify(newUserRegistrar).register(any());
        verify(newUserRewardService).award(9L);
    }

    @Test
    @DisplayName("findOrCreate — 昵称透传建档对象；空白昵称不覆盖（由建档方回退默认昵称）")
    void shouldApplyNicknameOnlyWhenPresent() {
        when(userRepository.findByIdentity(IdentityTypeEnum.PHONE, "13800138000"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(newUserRegistrar.register(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            assertEquals("冰美式", user.getNickname(), "非空昵称应透传到建档对象");
            return User.builder().id(11L).userId(user.getUserId()).build();
        });
        when(distributedLock.executeWithLock(anyString(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        provisioner.findOrCreate(IdentityTypeEnum.PHONE, "13800138000", " 冰美式 ");
    }

    @Test
    @DisplayName("findOrCreate — 并发唯一键冲突：回查既有记录（幂等兜底），不发建房奖励")
    void shouldReload_whenDuplicate() {
        User raced = User.builder().id(11L).userId("u-11").build();
        when(userRepository.findByIdentity(IdentityTypeEnum.PHONE, "13800138000"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(newUserRegistrar.register(any())).thenThrow(
                new DuplicateUserException(org.icedamericanomall.common.enums.ErrorCode.USER_ALREADY_EXISTS, "duplicated"));
        when(distributedLock.executeWithLock(anyString(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        User result = provisioner.findOrCreate(IdentityTypeEnum.PHONE, "13800138000", null);

        assertEquals(11L, result.getId(), "冲突后回查返回并发创建方的记录");
        verify(newUserRewardService, never()).award(any());
    }
}
