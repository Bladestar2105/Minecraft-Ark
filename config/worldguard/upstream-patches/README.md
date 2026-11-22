# WorldGuard 7.0.21 owned dinosaur hatch

WorldGuard uses the carrier's species for its regional `deny-spawn` check. The three carnivorous dinosaur hatchlings use Ravagers. On 2026-11-22 the ARK project added an optional hook to that check for a newly hatching owned dinosaur in the `ark` world's `adventure_beginner` region. ArkDinos supplies the permission for the duration of the native egg spawn. It evaluates overlapping regions, inherited and global restrictions with WorldGuard's flag resolver. The original check applies when ArkDinos is absent.

The JDK 25 transformer accepts the exact upstream JAR with SHA-256 `070223c535629f3f5d15f002388c1e3ae615a5f4ba051e7ccfb5d716540547a9`. Use separate input and output files.

```sh
java config/worldguard/upstream-patches/WorldGuardOwnedHatchFix.java upstream.jar worldguard-bukkit-7.0.21.jar
```

The runtime dependency manifest pins the upstream download and both checksums. The release bundle includes the derived JAR, transformer, license texts and this notice. Source for the original 7.0.21 build is available at [EngineHub/WorldGuard, revision da30a99](https://github.com/EngineHub/WorldGuard/tree/da30a99).

WorldGuard is copyright its respective authors. Its source and this modification are distributed under the GNU Lesser General Public License as provided by upstream. The upstream license includes the Priority R-Tree notice. [Upstream license](LICENSE-WorldGuard.txt) · [GNU General Public License](LICENSE-GPL-3.0.txt)
