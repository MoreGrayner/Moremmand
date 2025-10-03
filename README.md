# Moremmand

Kotlin Infix for Minecraft Commands

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF.svg?logo=kotlin)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-02303A.svg?logo=gradle)](https://gradle.org)

---

* ### Features
  * Kotlin _Infix_ 를 이용한 극단적으로 간결한 문법 지원
  * TabComplete 지원

* ### Supported MInecraft Versions
  * 1.21

---

마인크래프트 플러그인을 작성하다 보면 명령어를 구현할 일이 많습니다.  
Bukkit에서는 이러한 명령어를 구현할 수 있게 onCommamd()라는 메서드를 제공합니다.  


그러나 Bukkit의 API는 태생이 Java 코드인데다가 구현한 방식도 장황하기 짝이없어   
명령어 하나에 투자되는 시간이 기하급수적으로 늘어나게 됩니다.

이러한 간단한 명령어 구현을 최대한 가독성좋은 방식으로 날로먹기위한 프레임워크를 만들기로 했습니다.

---

간단한 명령어 예제를 봅시다.  
`/tp <Player> to <Location>`

Bukkit을 이용해 해당 명령어를 구현할 시 다음과 같은 과정을 따르게 됩니다.

`Kotlin`
```Kotlin
//플러그인 초기화 및 명령어 등록...
override fun onEnable() {
    getCommand("tp")?.setExecutor(this)
}

//명령어 정의...
override fun onCommand(
    sender: CommandSender,
    command: Command,
    label: String,
    args: Array<out String>
): Boolean {
    if (sender !is Player) return false
    if (!sender.isOp) return false

    if (command.name.equals("tp", ignoreCase = true)) {
        // 최소 4개의 인자 필요: 플레이어 이름, x, y, z
        if (args.size < 4) {
            sender.sendMessage("사용법: /tp <플레이어> <x> <y> <z>")
            return false
        }

        val targetPlayer = Bukkit.getPlayerExact(args[0])
        if (targetPlayer == null) return false

        val x = args[1].toDoubleOrNull()
        val y = args[2].toDoubleOrNull()
        val z = args[3].toDoubleOrNull()

        if (x == null || y == null || z == null) return false

        val world = sender.world
        val location = Location(world, x, y, z)
        targetPlayer.teleport(location)
        
        return true
    }

    return false
}
```

`plugin.yml`

```Yml
commands:
  tp:
    usage: /tp <Player> <Location>
```

최대한 간결하게 작성했음에도 명령어 하나에 꽤나 많은 코드가 작성되게 됩니다.  

게다가 TabComplete 등의 기능으로 명령어 자동완성을 구현하려고 하면  
코드가 이전보다 두배는 더 길어지는 기적같은 상황을 마주할 수 있습니다.  

다음은 _Moremmand_ 를 활용한 코드입니다.  

`Kotlin`

```Kotlin
//명령어 등록(plugin.yml 포함)과 권한 지정, 자동 완성, 인자 처리는 내부에서 진행됩니다.
//getLocation 함수의 경우 (X..Y) 형태의 IntRange 인자를 받습니다.
//Hint 함수는 명령어 자동 완성을 제공합니다.
//기타 옵션 없이 run{} 블록만 붙인 진행도 가능합니다.
//클래스명 중복으로 인해 부득이하게 이름을 변경하였습니다.
moremmand("tp") target(PLAYER) permission(anyof(listof())) run {
  val player = args.getPlayer(0) hint("이름입력") to server.getOnlinePlayers
  val location = args.getLocation(1..3) hint ("위치입력") to player.location
  player.teleport(location)  
  true
}
```
명령어 등록, 타겟지정, 자동완성을 포함한 명령어를 단 4줄만에 구현한 코드가 완성되었습니다.  

플러그인 초기화 시점에 전역 함수로 선언만 해두면 등록과 후처리까지 자동으로 처리해 활성화됩니다.

---

* ### Dependencies
  * build.gradle.kts
  ```kotlin
    repositories{
      maven("https://repo1.maven.org/maven2/")
    }
    dependencies {
      implementation("io.github.moregrayner.flowx:moremmand:version")
      //1.2 버전부터 시작합니다.
    }
  ```

* ### NOTE
  * 모든 코드는 SubCommand를 고려해 작성되지 않았습니다.
  * 문의사항이 있을 시 디스코드 MoreGrayner로 연락 바랍니다.
