package dev.mr3n.zipline.commands

import dev.mr3n.zipline.ZipLine
import dev.mr3n.zipline.items.ZipLineCreator
import net.md_5.bungee.api.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

object ZipLineCommand: CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        try {
            if(sender !is Player) { return true }
            when(args.getOrNull(0)) {
                // /zipline stickの処理
                "stick" -> {
                    // インベントリに棒を追加
                    sender.inventory.addItem(ZipLineCreator.clone())
                    // 追加した旨のメッセージを送信
                    sender.sendMessage("${ChatColor.GREEN}棒を入手しました。これでzipラインのルートをクリックして設定してください。")
                }
                // /zipline undoの処理
                "undo" -> {
                    if(ZipLine.PLAYER_OPERATIONS[sender]?.isEmpty()!=false) {
                        // if:操作履歴がからの場合
                        // 空のため取り消せない旨を送信
                        sender.sendMessage("${ChatColor.RED}これ以上操作を取りけれません。")
                    } else {
                        // if:操作履歴がある場合
                        // 最新の操作を削除
                        ZipLine.PLAYER_OPERATIONS[sender]?.removeAt(0)
                        // 成功のメッセージを送信
                        sender.sendMessage("${ChatColor.RED}操作を取り消しました。この操作は取り消せません。")
                    }
                }
                "create" -> {
                    // 新しい名前。指定されていない場合は指定しないといけないというエラーを表示
                    val name = args.getOrNull(1)?:throw Exception("名前を指定してください。/zipline create <name>")
                    // stickで接て～したルートを取得。空の場合はエラー
                    ZipLine.PLAYER_OPERATIONS[sender]
                        ?.also { if(it.size < 2) { throw Exception("ルートは最低２つ設定する必要があります。") } }
                        ?.forEachIndexed { index, location ->
                        ZipLine.INSTANCE.config.set("lines.${sender.world.name}.${name}.${index}", location)
                    }?:throw Exception("先に/zipline stickで棒を取得してルートを設定してください。")
                    // configをセーブ
                    ZipLine.INSTANCE.saveConfig()
                    // 成功した旨をメッセージで知らせる
                    sender.sendMessage("${ChatColor.GREEN}新しくジップラインを作成しました: ${name}")
                    // stickのルート設定を削除。
                    ZipLine.PLAYER_OPERATIONS.remove(sender)
                    // ZIP_LINES変数を更新して新しく設定したルートを適用する。
                    ZipLine.INSTANCE.reload()
                }
                "list" -> {
                    // プレイヤーのいるワールドのジップライン一覧を取得
                    val keys = ZipLine.INSTANCE.config.getConfigurationSection("lines.${sender.world.name}")?.getKeys(false)
                    // 一覧をプレイヤーに送信
                    sender.sendMessage("${ChatColor.GREEN}ジップライン一覧: ${keys?.joinToString(" ")?:"このワールドにはありません。"}")
                }
                "delete" -> {
                    // 削除するziplineの名前
                    val name = args.getOrNull(1)?:throw Exception("名前を指定してください。/zipline delete <name>")
                    // 削除
                    ZipLine.INSTANCE.config.set("lines.${sender.world.name}.${name}", null)
                    // config保存
                    ZipLine.INSTANCE.saveConfig()
                    // ZIP_LINES変数を更新して削除したziplineを適用する
                    ZipLine.INSTANCE.reload()
                }
                else -> {
                    // 存在しない引数を指定した場合はエラー
                    sender.sendMessage("そのコマンドは存在しません。")
                }
            }
        } catch(e: Exception) {
            sender.sendMessage("${ChatColor.RED}${e.message}")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        if(sender !is Player) { return mutableListOf() }
        when (args.size) {
            1 -> {
                return mutableListOf("stick", "undo", "create", "list", "delete")
            }
            2 -> {
                return when(args.getOrNull(1)) {
                    "create", "delete" -> {
                       ZipLine.ZIP_LINES[sender.world.name]?.keys?.toMutableList()?: mutableListOf()
                    }
                    else -> { mutableListOf() }
                }
            }
        }
        return mutableListOf()
    }
}