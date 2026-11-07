# libs/

## baritone-api-fabric-1.17.0.jar

| 项 | 内容 |
| --- | --- |
| 项目 | [cabaletta/baritone](https://github.com/cabaletta/baritone) |
| 版本 | 1.17.0（官方 `baritone-api-fabric` 构建） |
| SHA-1 | `553bf1f57179e7d765a6131659b62b4c865ed47e` |
| 许可证 | GNU Lesser General Public License v3.0 |
| 修改 | 无，原样保留 |

用途：`build.gradle` 的 `processResources` 会把它复制到主 jar 的 `META-INF/jars/`，
并在 `fabric.mod.json` 的 `jars` 数组中声明，作为 Fabric 嵌套 mod 随主 jar 分发，
因此玩家不需要单独安装 Baritone。

运行期 ChaosHelper 只通过 `baritone.api.**` 的公开接口反射调用它，不复制其源码，
也不修改其行为。完整的第三方声明与源码获取方式见仓库根目录的 `NOTICE`。

升级 Baritone 时：替换本文件 → 更新 `build.gradle` 与 `fabric.mod.json` 中的文件名 →
重新核对 `BaritoneBridge` 的接口签名 → 更新本文件与 `NOTICE`。
