package org.icedamericanomall.auth.app.port.risk;

/**
 * 设备信任判断能力（应用层端口，风控维度子端口）。
 */
public interface DeviceTrustService {

    /** 该设备是否已被标记为可信 */
    boolean isTrusted(String deviceId);
}
