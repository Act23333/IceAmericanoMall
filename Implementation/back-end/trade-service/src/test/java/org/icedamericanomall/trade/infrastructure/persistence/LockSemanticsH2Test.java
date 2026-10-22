package org.icedamericanomall.trade.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前置实验：H2 的 {@code SELECT ... FOR UPDATE} 是否**真的阻塞**第二个会话。
 *
 * <p><b>为什么这是个实验而不是常识</b>：整套「同一提现单最多一个非终态尝试」的不变量，
 * 全靠 `FOR UPDATE` 把并发挡在行锁外。若 H2 不阻塞，那么并发用例在本机永远绿，
 * 上生产才爆 —— 那是最坏的一种绿。
 *
 * <p>判定方式：T1 持锁后，T2 设 **短锁超时** 再取同一行锁：
 * <ul>
 *   <li>若 300ms 内抛锁超时 ⇒ **真的阻塞**（可用作不变量用例的真库基础）；</li>
 *   <li>若立即成功 ⇒ **不阻塞** ⇒ 只能登记「串行等价」，不得声称并发已验。</li>
 * </ul>
 */
@DisplayName("H2 行锁语义（FOR UPDATE 是否真阻塞）")
class LockSemanticsH2Test {

    private static final String URL =
            "jdbc:h2:mem:lockdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test
    @DisplayName("T1 持 FOR UPDATE 时，T2 取同一行必须超时；T1 释放后 T2 可取到")
    void selectForUpdateShouldBlockSecondSession() throws Exception {
        try (Connection setup = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = setup.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS lock_row (id BIGINT PRIMARY KEY, v INT NOT NULL)");
            stmt.execute("DELETE FROM lock_row");
            stmt.execute("INSERT INTO lock_row(id, v) VALUES (1, 0)");
        }

        try (Connection t1 = DriverManager.getConnection(URL, "sa", "");
             Connection t2 = DriverManager.getConnection(URL, "sa", "")) {
            t1.setAutoCommit(false);
            t2.setAutoCommit(false);

            // T1 持锁
            try (Statement s = t1.createStatement()) {
                s.executeQuery("SELECT * FROM lock_row WHERE id = 1 FOR UPDATE");
            }

            // T2 短超时取同一行 ⇒ 期望超时（= 真阻塞）
            try (Statement s = t2.createStatement()) {
                s.execute("SET LOCK_TIMEOUT 300");
            }
            Instant start = Instant.now();
            SQLException blocked = assertThrows(SQLException.class, () -> {
                try (Statement s = t2.createStatement()) {
                    s.executeQuery("SELECT * FROM lock_row WHERE id = 1 FOR UPDATE");
                }
            }, "T2 未阻塞即证明 H2 的 FOR UPDATE 不生效 —— 此时并发用例只能登记为串行等价");
            Duration waited = Duration.between(start, Instant.now());

            assertTrue(waited.toMillis() >= 250,
                    "T2 应在锁超时线上等待（实际等待 " + waited.toMillis() + "ms），而不是立刻失败");
            assertTrue(blocked.getMessage().toLowerCase().contains("timeout")
                            || blocked.getMessage().toLowerCase().contains("lock"),
                    "期望锁超时类错误，实际：" + blocked.getMessage());

            // T1 释放后 T2 必须能取到锁（证明阻塞来自行锁，不是别的错）
            t1.rollback();
            t2.setAutoCommit(true);
            try (Statement s = t2.createStatement()) {
                s.executeQuery("SELECT * FROM lock_row WHERE id = 1 FOR UPDATE");
            }
        }
    }
}
