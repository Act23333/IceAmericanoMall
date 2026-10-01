package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.user.domain.exception.DuplicateUserException;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.port.DistributedLock;
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
 * 用户"查找或创建"领域服务单元测试 —— 幂等三层防线（先查短路 / 锁内双重检查 / 冲突回查）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserProvisioner 领域单元测试")
class UserProvisionerTest {

    @Mock UserRepository userRepository;
    @Mock DistributedLock distributedLock;

    private UserProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new UserProvisioner(userRepository, distributedLock);
    }

    @Test
    @DisplayName("findOrCreate — 已存在：直接返回，不加锁不插入")
    void shouldReturnExisting_withoutLock() {
        User existing = User.builder().id(7L).userId("u-7").phone("13800138000").build();
        when(userRepository.findByIdentity(IdentityTypeEnum.PHONE, "13800138000"))
                .thenReturn(Optional.of(existing));

        User result = provisioner.findOrCreate(IdentityTypeEnum.PHONE, "13800138000");

        assertEquals(7L, result.getId());
        verify(distributedLock, never()).executeWithLock(anyString(), any());
        verify(userRepository, never()).insert(any());
    }

    @Test
    @DisplayName("findOrCreate — 不存在：锁内双重检查（第二次仍未命中）后创建")
    void shouldCreate_whenAbsent() {
        User created = User.builder().id(9L).userId("u-9").phone("13800138000").build();
        when(userRepository.findByIdentity(IdentityTypeEnum.PHONE, "13800138000"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(userRepository.insert(any())).thenReturn(created);
        // 锁端口直通临界区
        when(distributedLock.executeWithLock(anyString(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        User result = provisioner.findOrCreate(IdentityTypeEnum.PHONE, "13800138000");

        assertEquals(9L, result.getId());
        verify(userRepository).insert(any());
    }

    @Test
    @DisplayName("findOrCreate — 并发唯一键冲突：回查既有记录（幂等兜底）")
    void shouldReload_whenDuplicate() {
        User raced = User.builder().id(11L).userId("u-11").phone("13800138000").build();
        when(userRepository.findByIdentity(IdentityTypeEnum.PHONE, "13800138000"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(userRepository.insert(any())).thenThrow(
                new DuplicateUserException(org.icedamericanomall.common.enums.ErrorCode.USER_ALREADY_EXISTS, "duplicated"));
        when(distributedLock.executeWithLock(anyString(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        User result = provisioner.findOrCreate(IdentityTypeEnum.PHONE, "13800138000");

        assertEquals(11L, result.getId(), "冲突后回查返回并发创建方的记录");
    }
}
