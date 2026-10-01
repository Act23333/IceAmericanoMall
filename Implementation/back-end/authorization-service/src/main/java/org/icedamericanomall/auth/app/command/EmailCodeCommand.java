package org.icedamericanomall.auth.app.command;

import lombok.Builder;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;

/**
 * 邮箱验证码签发命令（应用层入参，写操作）。
 *
 * @param email  收件邮箱（明文，领域层用值对象归一化与校验）
 * @param scene  业务场景（前端可不传，适配层缺省为 LOGIN）
 * @param clientIp 客户端 IP（仅用于日志归因）
 */
@Builder
public record EmailCodeCommand(String email,
                               VerificationSceneEnum scene,
                               String clientIp) {

    /** 场景缺省值兜底（防御式：命令可不经适配层构造） */
    public EmailCodeCommand {
        scene = scene == null ? VerificationSceneEnum.DEFAULT : scene;
    }
}
