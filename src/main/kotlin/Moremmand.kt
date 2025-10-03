package io.github.moregrayner.flowx

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.plugin.java.JavaPlugin

sealed class TargetType

data object PLAYER : TargetType()
data object CONSOLE : TargetType()
data object ANY : TargetType()

sealed class PermissionCheck
data class AnyOf(val permissions: List<String>) : PermissionCheck()
data class AllOf(val permissions: List<String>) : PermissionCheck()

fun anyOf(permissions: List<String>) = AnyOf(permissions)
fun allOf(permissions: List<String>) = AllOf(permissions)

class MoremmandBuilder(private val name: String, private val plugin: JavaPlugin) {
    private var targetType: TargetType = ANY
    private var permissionCheck: PermissionCheck? = null
    private var executor: (CommandContext.() -> Boolean)? = null
    internal val argumentHints = mutableListOf<ArgumentHintData>()

    infix fun target(type: TargetType): MoremmandBuilder {
        this.targetType = type
        return this
    }

    infix fun permission(check: PermissionCheck): MoremmandBuilder {
        this.permissionCheck = check
        return this
    }

    infix fun run(block: CommandContext.() -> Boolean): MoremmandBuilder {
        this.executor = block
        register()
        return this
    }

    private fun register() {
        val command = plugin.getCommand(name)

        if (command == null) {
            try {
                val commandMap = try {
                    plugin.server.commandMap
                } catch (e: NoSuchMethodError) {
                    val field = plugin.server.javaClass.getDeclaredField("commandMap")
                    field.isAccessible = true
                    field.get(plugin.server) as org.bukkit.command.CommandMap
                }

                commandMap.getCommand(name)?.unregister(commandMap)

                val cmd = object : Command(name) {
                    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
                        return handleCommand(sender, args)
                    }

                    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
                        return handleTabComplete(sender, args)
                    }
                }

                commandMap.register(plugin.name, cmd)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to register command $name: ${e.message}")
            }
            return
        }

        command.setExecutor(CommandExecutor { sender, _, _, args -> handleCommand(sender, args) })

        command.tabCompleter = org.bukkit.command.TabCompleter { sender, _, _, args ->
            handleTabComplete(sender, args)
        }
    }

    private fun validateSender(sender: CommandSender, showMessage: Boolean = true): Boolean {
        when (targetType) {
            is PLAYER -> if (sender !is org.bukkit.entity.Player) {
                if (showMessage) sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                return false
            }
            is CONSOLE -> if (sender !is ConsoleCommandSender) {
                if (showMessage) sender.sendMessage("§c이 명령어는 콘솔에서만 사용할 수 있습니다.")
                return false
            }
            is ANY -> {  }
        }

        permissionCheck?.let { check ->
            val hasPermission = when (check) {
                is AnyOf -> check.permissions.any { sender.hasPermission(it) }
                is AllOf -> check.permissions.all { sender.hasPermission(it) }
            }
            if (!hasPermission) {
                if (showMessage) sender.sendMessage("§c권한이 없습니다.")
                return false
            }
        }

        return true
    }

    private fun handleTabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (!validateSender(sender, showMessage = false)) return emptyList()

        val currentArgIndex = args.size - 1

        if (currentArgIndex >= 0 && currentArgIndex < argumentHints.size) {
            val hintData = argumentHints[currentArgIndex]
            val currentInput = args.lastOrNull() ?: ""

            val completions = hintData.completionSource?.invoke(sender, plugin) ?: return listOf("<${hintData.hintText}>")

            return completions.filter { it.startsWith(currentInput, ignoreCase = true) }
        }

        return emptyList()
    }

    private fun handleCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (!validateSender(sender)) return false

        // Execute command
        val context = CommandContext(sender, args, plugin, this)
        return executor?.invoke(context) ?: false
    }
}

class CommandContext(
    val sender: CommandSender,
    private val rawArgs: Array<out String>,
    val plugin: JavaPlugin,
    private val builder: MoremmandBuilder
) {
    val args = ArgumentParser(rawArgs, plugin, builder)
    val player: org.bukkit.entity.Player? = sender as? org.bukkit.entity.Player

    fun success(message: String) {
        sender.sendMessage("§a$message")
    }

    fun error(message: String) {
        sender.sendMessage("§c$message")
    }
}

class ArgumentParser(
    private val args: Array<out String>,
    private val plugin: JavaPlugin,
    private val builder: MoremmandBuilder
) {
    fun getString(index: Int): ArgumentResult<String?> {
        return ArgumentResult(args.getOrNull(index), index, builder, plugin)
    }

    fun getInt(index: Int): ArgumentResult<Int?> {
        return ArgumentResult(args.getOrNull(index)?.toIntOrNull(), index, builder, plugin)
    }

    fun getDouble(index: Int): ArgumentResult<Double?> {
        return ArgumentResult(args.getOrNull(index)?.toDoubleOrNull(), index, builder, plugin)
    }

    fun getPlayer(index: Int): ArgumentResult<org.bukkit.entity.Player?> {
        val name = args.getOrNull(index)
        val player = name?.let { plugin.server.getPlayer(it) }
        return ArgumentResult(player, index, builder, plugin)
    }

    fun getWorld(index: Int): ArgumentResult<org.bukkit.World?> {
        val name = args.getOrNull(index)
        val world = name?.let { plugin.server.getWorld(it) }
        return ArgumentResult(world, index, builder, plugin)
    }

    fun getMaterial(index: Int): ArgumentResult<Material?> {
        val name = args.getOrNull(index)
        val material = name?.let {
            try { Material.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }
        return ArgumentResult(material, index, builder, plugin)
    }

    fun getLocation(range: IntRange): ArgumentResult<Location?> {
        if (range.count() != 3) return ArgumentResult(null, range.first, builder, plugin)
        val coords = range.map { args.getOrNull(it)?.toDoubleOrNull() ?: return ArgumentResult(null, range.first, builder, plugin) }
        val world = plugin.server.worlds.firstOrNull() ?: return ArgumentResult(null, range.first, builder, plugin)
        return ArgumentResult(Location(world, coords[0], coords[1], coords[2]), range.first, builder, plugin)
    }

    fun getLocation(xIndex: Int, yIndex: Int, zIndex: Int, world: org.bukkit.World? = null): ArgumentResult<Location?> {
        val x = args.getOrNull(xIndex)?.toDoubleOrNull() ?: return ArgumentResult(null, xIndex, builder, plugin)
        val y = args.getOrNull(yIndex)?.toDoubleOrNull() ?: return ArgumentResult(null, xIndex, builder, plugin)
        val z = args.getOrNull(zIndex)?.toDoubleOrNull() ?: return ArgumentResult(null, xIndex, builder, plugin)
        val w = world ?: plugin.server.worlds.firstOrNull() ?: return ArgumentResult(null, xIndex, builder, plugin)
        return ArgumentResult(Location(w, x, y, z), xIndex, builder, plugin)
    }

    fun getAllFrom(index: Int): ArgumentResult<String> {
        return ArgumentResult(args.drop(index).joinToString(" "), index, builder, plugin)
    }

    val size: Int get() = args.size
}

data class ArgumentHintData(
    val hintText: String,
    val completionSource: ((CommandSender, JavaPlugin) -> List<String>)?
)

class ArgumentResult<T>(
    val value: T,
    private val index: Int,
    private val builder: MoremmandBuilder,
    private val plugin: JavaPlugin
) {
    private var currentHintText: String = ""

    infix fun hint(description: String): ArgumentResult<T> {
        this.currentHintText = description
        return this
    }

    infix fun to(source: kotlin.Any): T {
        val completionSource: (CommandSender, JavaPlugin) -> List<String> = when (source) {
            is Collection<*> -> { _, _ ->
                source.mapNotNull {
                    when (it) {
                        is String -> it
                        is org.bukkit.entity.Player -> it.name
                        is org.bukkit.World -> it.name
                        is Material -> it.name.lowercase()
                        is org.bukkit.entity.Entity -> it.customName
                        else -> it?.toString()
                    }
                }
            }

            is org.bukkit.entity.Player -> { _, _ -> listOf(source.name) }
            is org.bukkit.World -> { _, _ -> listOf(source.name) }
            is Material -> { _, _ -> listOf(source.name.lowercase()) }
            is org.bukkit.entity.Entity -> { _, _ ->
                listOfNotNull(source.customName)
            }

            is Class<*> -> { sender, p ->
                when {
                    org.bukkit.entity.Player::class.java.isAssignableFrom(source) -> {
                        p.server.onlinePlayers.map { it.name }
                    }
                    org.bukkit.World::class.java.isAssignableFrom(source) -> {
                        p.server.worlds.map { it.name }
                    }
                    Material::class.java.isAssignableFrom(source) -> {
                        Material.entries.filter { it.isItem }.map { it.name.lowercase() }
                    }
                    org.bukkit.entity.Entity::class.java.isAssignableFrom(source) -> {
                        if (sender is org.bukkit.entity.Player) {
                            sender.world.entities.mapNotNull {
                                it.customName ?: (it as? org.bukkit.entity.Player)?.name
                            }
                        } else emptyList()
                    }
                    else -> emptyList()
                }
            }

            is Function1<*, *> -> { sender, _ ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val result = (source as Function1<CommandSender, kotlin.Any>).invoke(sender)
                    when (result) {
                        is Collection<*> -> result.mapNotNull { it?.toString() }
                        else -> listOf(result.toString())
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            is String -> { _, _ -> listOf(source) }

            is Array<*> -> { _, _ ->
                source.mapNotNull {
                    when (it) {
                        is String -> it
                        is org.bukkit.entity.Player -> it.name
                        is org.bukkit.World -> it.name
                        is Material -> it.name.lowercase()
                        else -> it?.toString()
                    }
                }
            }

            else -> { _, _ -> listOf(source.toString()) }
        }

        while (builder.argumentHints.size <= index) {
            builder.argumentHints.add(ArgumentHintData("", null))
        }
        builder.argumentHints[index] = ArgumentHintData(currentHintText, completionSource)
        return value
    }
}

fun moremmand(name: String): MoremmandBuilder {
    // Try to find the calling plugin from Bukkit's plugin manager
    val plugin = try {
        val stackTrace = Thread.currentThread().stackTrace
        val callerClassName = stackTrace
            .firstOrNull { element ->
                val className = element.className
                !className.startsWith("io.github.moremmand") &&
                        !className.startsWith("java.") &&
                        !className.startsWith("kotlin.") &&
                        !className.startsWith("jdk.")
            }?.className

        callerClassName?.let { className ->
            org.bukkit.Bukkit.getPluginManager().plugins.firstOrNull { plugin ->
                try {
                    // Check if the caller class belongs to this plugin
                    plugin.javaClass.classLoader.loadClass(className)
                    true
                } catch (e: ClassNotFoundException) {
                    false
                }
            } as? JavaPlugin
        }
    } catch (e: Exception) {
        null
    } ?: throw IllegalStateException(
        "Could not automatically detect plugin instance. Make sure moremmand is called from within a JavaPlugin."
    )

    return MoremmandBuilder(name, plugin)
}

fun JavaPlugin.moremmand(name: String): MoremmandBuilder {
    return MoremmandBuilder(name, this)
}