#!/usr/bin/env bash
# 统一门禁入口 —— **本地 hook 与 CI 都调用本脚本的同一份定义**，避免「hook 绿、CI 红」漂移。
#
# 背景：曾出现「工作区红了一上午、交接单还写着全绿」。根因不是 CI 不跑测试（CI 的 build job
# 一直有 `mvn test`），而是**红基线从未提交**，CI 根本看不到它。唯一有效的补法：把门禁挪到
# 提交动作上（`.githooks/`），并且让门禁只有一处定义 —— 就是本文件。
#
# 用法:
#   bash verify-gates.sh fast          # 提交前：秒级
#   bash verify-gates.sh full          # 推送前 / CI：全量，等价
#   bash verify-gates.sh self-test     # 门禁自身的变异测试：喂合成违规样本，断言门禁必须红
#   bash verify-gates.sh matrix        # 反 flaky：同一门禁在「单类 / 整模块」两个入口结论必须一致
#   bash verify-gates.sh install-hooks # 一次性安装 git 钩子（幂等）
#
# 环境变量:
#   MVN_ARGS=""  让 mvn 走在线模式（CI 用；本地默认 -o 离线）
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BE="$ROOT/Implementation/back-end"
MODE="${1:-fast}"
MVN_ARGS="${MVN_ARGS--o}"      # 本地默认离线；CI: `MVN_ARGS="" bash verify-gates.sh full`

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

# 安静跑一次 mvn，只回传成败（供 matrix 比较结论，不刷屏）
mvn_verdict() {
  ( cd "$BE" && mvn $MVN_ARGS -q "$@" >/dev/null 2>&1 ) && echo PASS || echo FAIL
}

fast_steps() {
  run "交接单未复述可测量事实" bash "$BE/scripts/check-ledger-facts.sh"
  run "根文档计数与实测一致"   node "$BE/scripts/sync-doc-counts.mjs" --check --strict-tests
  run "ID 不变量（ADR-050/051）" bash "$BE/scripts/check-id-invariants.sh"
  run "ia-api 单测（含降级语义契约门禁）" mvn $MVN_ARGS -q -f "$BE/pom.xml" -pl ia-api test
}

case "$MODE" in
  fast)
    fast_steps
    ;;
  full)
    fast_steps
    run "全量 mvn test（含 architecture-tests 全部守卫）" mvn $MVN_ARGS -f "$BE/pom.xml" test
    ;;
  self-test)
    # 门禁自身的变异测试：**故意喂违规样本，断言门禁会红**。
    # 「跑一遍真实仓库全绿」什么都证明不了 —— 它可能是一条恒为空转的规则。
    run "交接单门禁自证（12 类应拦样本 + 4 类应放行样本）" \
        bash "$BE/scripts/check-ledger-facts.sh" --self-test
    # Java 侧自证（在 mvn test 内）：
    #   FallbackDegradeContractTest#shouldFlagSyntheticClientThatRewritesClientErrorToSystemBusy
    #   FallbackDegradeContractTest#shouldFlagSyntheticClientThatSwallowsUnavailableFailure
    #     （合成违规体 RewriteToBusyFactory / SilentFailOpenFactory）
    #   FallbackCategoryGuardTest 的 fixture 变异、ClassSizeRatchetTest 的四条规则合成数据单测
    run "Java 侧自证（在 mvn test 内跑）" \
        mvn $MVN_ARGS -q -f "$BE/pom.xml" -pl ia-api,architecture-tests test
    ;;
  matrix)
    # 反 flaky：同一门禁在「单类」与「整模块」两个入口下结论必须一致。
    # 结论不一致 ⇒ 该门禁依赖了执行顺序/JVM 状态/临时目录，属 flaky，必须修掉而不是记住「它有时红」。
    printf '\n── 反 flaky 矩阵（单类 vs 整模块）──\n'
    mvn $MVN_ARGS -q -f "$BE/pom.xml" -pl ia-common,ia-api -am test-compile >/dev/null 2>&1

    check_one() {   # $1=module  $2=测试类
      local single module_wide
      single="$(mvn_verdict -f "$BE/pom.xml" -pl "$1" test -Dtest="$2" -DfailIfNoTests=false)"
      module_wide="$(mvn_verdict -f "$BE/pom.xml" -pl "$1" test)"
      if [ "$single" = "$module_wide" ]; then
        printf '  ✓ %-32s 单类=%-4s 整模块=%-4s（一致）\n' "$2" "$single" "$module_wide"
      else
        printf '  ✗ %-32s 单类=%-4s 整模块=%-4s（**结论不一致 = flaky**）\n' "$2" "$single" "$module_wide"
        fail=1
      fi
    }

    check_one architecture-tests ArchitectureTest
    check_one architecture-tests ClassSizeRatchetTest
    check_one architecture-tests FallbackCategoryGuardTest
    check_one ia-api             FallbackDegradeContractTest
    [ "$fail" -eq 0 ] || echo "  判据：结论不一致即 flaky；单个入口全绿不足以证明门禁可信。"
    ;;
  install-hooks)
    cd "$ROOT"
    git config core.hooksPath .githooks
    printf '✓ core.hooksPath = %s\n' "$(git config --get core.hooksPath)"
    for h in pre-commit pre-push; do
      if [ -f ".githooks/$h" ]; then printf '  ✓ .githooks/%s 存在\n' "$h"; else printf '  ✗ 缺少 .githooks/%s\n' "$h"; fail=1; fi
    done
    printf '  pre-commit → fast（秒级） / pre-push → full（全量）\n'
    ;;
  *)
    echo "用法: bash verify-gates.sh fast|full|self-test|matrix|install-hooks" >&2
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
