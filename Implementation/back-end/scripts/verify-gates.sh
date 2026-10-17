#!/usr/bin/env bash
# 统一门禁入口 —— **本地与 CI 用同一份定义**，避免「我记得跑过」型的假绿。
#
# 背景：曾出现「工作区红了一上午、交接单还写着全绿」的情况。根因不是 CI 不跑测试
# （CI 的 build job 有 `mvn test`），而是**红基线从未提交**，于是 CI 根本看不到它。
# 唯一有效的补法：把门禁挪到提交动作上（`.githooks/`），并且让门禁只有一处定义。
#
# 用法:
#   bash verify-gates.sh fast   # 提交前：秒级，拦「文档/契约与代码不一致」与 ia-api 单测
#   bash verify-gates.sh full   # 推送前：全量，等价于 CI
#
# 一次性安装钩子（每个 clone 一次）:
#   git config core.hooksPath .githooks
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BE="$ROOT/Implementation/back-end"
MODE="${1:-fast}"
MVN_ARGS="${MVN_ARGS:--o}"      # 本地默认离线；CI/无网限制时 `MVN_ARGS="" bash verify-gates.sh full`

# JDK/Maven：本地是固定路径（不在 PATH 上），CI 由 setup-java 提供 —— 两边都不硬失败
if [ -z "${JAVA_HOME:-}" ] && [ -d /c/Users/18279/.jdks/ms-21.0.10 ]; then
  export JAVA_HOME=/c/Users/18279/.jdks/ms-21.0.10
fi
if [ -d /e/maven/apache-maven-3.9.7/bin ]; then
  export PATH="/e/maven/apache-maven-3.9.7/bin:${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
fi

fail=0
run() {
  local name="$1"; shift
  printf '\n── %s ──\n' "$name"
  if "$@"; then
    echo "✓ $name"
  else
    echo "✗ $name"
    fail=1
  fi
}

case "$MODE" in
  fast)
    run "交接单未复述可测量事实"      bash "$BE/scripts/check-ledger-facts.sh"
    run "根文档计数与实测一致"        node "$BE/scripts/sync-doc-counts.mjs" --check --strict-tests
    run "ID 不变量（ADR-050/051）"    bash "$BE/scripts/check-id-invariants.sh"
    run "ia-api 单测（含降级语义契约门禁）" \
        mvn $MVN_ARGS -q -f "$BE/pom.xml" -pl ia-api test
    ;;
  full)
    "$0" fast || fail=1
    run "全量 mvn test（含 architecture-tests 全部守卫）" \
        mvn $MVN_ARGS -f "$BE/pom.xml" test
    ;;
  self-test)
    # 门禁自身的变异测试：**故意喂违规样本，断言门禁会红**。
    # 只有这一层能证明「门禁会咬人」——「跑一遍真实仓库全绿」什么都证明不了。
    # Java 侧的自证在 mvn test 内：
    #   FallbackDegradeContractTest#shouldFlagSyntheticClientThatRewritesClientErrorToSystemBusy
    #   FallbackDegradeContractTest#shouldFlagSyntheticClientThatSwallowsUnavailableFailure
    #     （合成违规体 RewriteToBusyFactory / SilentFailOpenFactory）
    #   FallbackCategoryGuardTest 的 fixture 变异、ClassSizeRatchetTest 的四条规则合成数据单测。
    echo "── 交接单门禁：合成违规样本必须红、[历史] 必须豁免 ──"
    tmp="$(mktemp)"
    printf '%s\n' \
      '| 测试 | 2235 / 0 failures |' \
      '| 表 | 62 张表 |' \
      '| 对账 | matched 289 / codeOnly 0 |' \
      '- 已改 16 / 30 已改' \
      '| 历史 | 曾 2254 测试 / 0 failures [历史] |' >"$tmp"
    if LEDGER_PATH="$tmp" bash "$BE/scripts/check-ledger-facts.sh" >/dev/null 2>&1; then
      echo "✗ 门禁对违规样本没有变红 —— 门禁失效"
      fail=1
    else
      echo "✓ 门禁对违规样本如期变红"
    fi
    rm -f "$tmp"
    run "Java 侧自证（在 mvn test 内跑）" \
        mvn $MVN_ARGS -q -f "$BE/pom.xml" -pl ia-api,architecture-tests test
    ;;
  *)
    echo "用法: bash verify-gates.sh fast|full|self-test" >&2
    exit 2
    ;;
esac

printf '\n════════════════════════════\n'
if [ "$fail" -eq 0 ]; then
  echo "门禁全通过（mode=$MODE）"
  exit 0
fi
echo "门禁未通过（mode=$MODE）—— 不要在这个状态下说『已完成』，也不要提交/推送。" >&2
exit 1
