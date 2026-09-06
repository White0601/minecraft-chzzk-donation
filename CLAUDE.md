# minecraft-chzzk-donation

치지직(Chzzk) 후원 이벤트를 받아서 Minecraft 클라이언트에 이펙트(타이틀, 몹 소환, TNT, 즉사, 인벤세이브, 아이템 지급, 포션 등)를 적용하는 Fabric 모드.

**개발 환경이 PC 2대로 나뉘어 있음** (git 저장소 아님 — 두 PC 간 파일을 수동으로 동기화해야 함):
- **서버 PC**: 치지직 API 폴링(`DonationPoller`), 큐/설정 관련 코드(`DonationQueue`, `ModConfig`, `DonationEvent`, `ChzzkDonationMod`) 담당. 인터넷으로 치지직 서버에 접근해야 해서 이 PC에서 돌림.
- **마크 PC** (현재 세션): 실제 Minecraft 클라이언트 실행/빌드 테스트, `DonationEffects`(바닐라 MC API를 직접 쓰는 이펙트 로직) 담당.

한쪽에서 수정한 내용은 이 파일에 정리해서 반대쪽 PC의 Claude Code가 읽고 반영하도록 한다.

## 빌드 환경 (마크 PC 기준으로 확정됨)

- **Minecraft**: 26.2 (비난독화 버전 — `mappings loom.officialMojangMappings()` 쓰면 안 됨, 아예 `mappings` 선언 자체를 하지 않음)
- **Fabric Loom**: 1.17 (plugin 버전 문자열은 `1.17-SNAPSHOT`, 공식 fabric-example-mod 26.2 브랜치도 동일하게 씀)
- **Gradle**: 9.5.1 (8.10은 Java 25를 지원 안 해서 사용 불가)
- **JDK**: 25 필수 (`sourceCompatibility`/`targetCompatibility`/`release` 전부 25). Gradle 9.1.0 미만은 JDK 25로 실행 자체가 안 됨.
- 두 PC 모두 로컬에 **JDK 25**, 그리고 프로젝트에 **정상적인 Gradle 9.5.1 wrapper**(`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`)가 있어야 함. wrapper가 없거나 깨져 있으면 `gradle wrapper --gradle-version 9.5.1`로 재생성.
- 빌드 시 `JAVA_HOME`을 JDK 25 경로로 잡아줘야 함 (예: PowerShell `$env:JAVA_HOME = "...\jdk-25.x.x"; .\gradlew.bat build`).

## `build.gradle` 관련 주의사항 (이미 수정 완료, 다른 PC 사본도 아래 상태와 일치해야 함)

- `plugins { id 'net.fabricmc.fabric-loom' version "${loom_version}" }` — `${project.loom_version}`처럼 `project.` 접두사를 붙이면 Gradle 9.x의 plugins 블록 파서가 컴파일 에러를 냄. **반드시 `${loom_version}`(project 없이) 형태로 써야 함.**
- `dependencies` 블록에 `mappings loom.officialMojangMappings()`를 넣으면 안 됨 → "Cannot use Mojang mappings in a non-obfuscated environment" 에러. 26.2는 비난독화라 mappings 선언 자체가 불필요.
- `fabric-loader`, `fabric-api` 의존성은 `modImplementation`이 아니라 `implementation`으로 선언 (비난독화 환경에서는 리매핑이 필요 없어서 공식 템플릿도 `implementation` 사용).

## Minecraft 26.2 바닐라 API 변경사항 (`DonationEffects.java`에서 확인된 것들)

이전 버전 API로 작성된 코드가 26.2에서 컴파일 안 되는 경우가 많음. 실제 26.2 jar(`javap`)로 확인한 변경 내역:

| 이전 | 26.2 | 비고 |
|---|---|---|
| `EntityType.ZOMBIE`, `.SKELETON` 등 상수 | `EntityTypes.ZOMBIE`, `.SKELETON` 등 (import `net.minecraft.world.entity.EntityTypes`) | 모든 엔티티 상수가 `EntityType` 클래스에서 별도 `EntityTypes`(복수형) 클래스로 이동. `EntityType<?>` 타입 선언 자체는 그대로 `EntityType` 사용. `BlockEntityType`도 동일 패턴으로 `BlockEntityTypes`에 상수 있음. |
| `MobEffects.MOVEMENT_SPEED` | `MobEffects.SPEED` | |
| `MobEffects.MOVEMENT_SLOWDOWN` | `MobEffects.SLOWNESS` | |
| `MobEffects.DIG_SPEED` | `MobEffects.HASTE` | |
| `MobEffects.DIG_SLOWDOWN` | `MobEffects.MINING_FATIGUE` | |
| `MobEffects.DAMAGE_BOOST` | `MobEffects.STRENGTH` | |
| `MobEffects.JUMP` | `MobEffects.JUMP_BOOST` | |
| `MobEffects.CONFUSION` | `MobEffects.NAUSEA` | |
| `MobEffects.DAMAGE_RESISTANCE` | `MobEffects.RESISTANCE` | |
| (그 외 `REGENERATION`, `FIRE_RESISTANCE`, `WATER_BREATHING`, `INVISIBILITY`, `BLINDNESS`, `NIGHT_VISION`, `HUNGER`, `WEAKNESS`, `POISON`, `WITHER`, `HEALTH_BOOST`, `ABSORPTION`, `LEVITATION`, `LUCK`, `UNLUCK`, `SLOW_FALLING`, `GLOWING`, `DARKNESS`는 이름 그대로 유지) | | |
| `net.minecraft.world.level.GameRules` | `net.minecraft.world.level.gamerules.GameRules` | 패키지 이동 |
| `GameRules.RULE_KEEPINVENTORY` | `GameRules.KEEP_INVENTORY` | |
| `level.getGameRules().getRule(RULE_X).set(value, server)` | `level.getGameRules().set(GameRules.RULE_X, value, server)` | `getRule()` 거치지 않고 `GameRules` 인스턴스에서 바로 `set`/`get` 호출하는 구조로 단순화됨 |
| `client.gui.setTitle/setSubtitle/setTimes(...)` | `client.gui.hud.setTitle/setSubtitle/setTimes(...)` | 타이틀/서브타이틀 렌더링이 `Gui`에서 새로 생긴 `Gui.hud` (`net.minecraft.client.gui.Hud`) 필드로 이동 |
| `entity.hurt(ServerLevel, DamageSource, float)` | `entity.hurt(DamageSource, float)` | `hurt`가 다시 2-인자 시그니처로 (level 인자 제거) |

`Items.*`(아이템 상수)는 이번 버전에서 변경 없이 그대로 사용 가능함을 확인함.

`DonationPoller.java`, `DonationQueue.java`, `ModConfig.java`, `DonationEvent.java`, `ChzzkDonationMod.java`는 바닐라 MC 클래스를 직접 참조하지 않아 이번 26.2 마이그레이션의 영향을 받지 않음 (컴파일 확인 완료).
