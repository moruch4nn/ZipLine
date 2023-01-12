package dev.mr3n.zipline.items

import dev.moru3.minepie.events.EventRegister.Companion.registerEvent
import dev.mr3n.zipline.ZipLine
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * ジップラインのルートを作成するための棒
 */
object ZipLineCreator: ItemStack(Material.STICK) {
    // アイテムタイプのNBTのKey
    private val ITEM_TYPE = NamespacedKey(ZipLine.INSTANCE, "ITEM_TYPE")
    // アイテムタイプのNBTのValue
    private const val ZIP_LINE_TYPE = "ZIP_LINE"

    // この棒を使用してブロックをクリックした際に呼び出される関数
    fun onClickBlock(event: PlayerInteractEvent) {
        // クリックしたブロック(nullだった場合return)
        val clickedBlock = event.clickedBlock?:return
        // 作成中のルート一覧
        val locations = ZipLine.PLAYER_OPERATIONS[event.player]?: mutableListOf()
        // ルート一覧に追加する新しいLocation
        val location = clickedBlock.location.add(.5,.5,.5)
        // ブロックを破壊しないようにイベントをキャンセル
        event.isCancelled = true
        if(!locations.any { it.blockX==location.blockX&&it.blockY==location.blockY&&it.blockZ==location.blockZ }) {
            // if:ルートに重複がない場合は追加
            locations.add(location)
            // 追加したlist(ローカル変数)をグローバル変数に格納
            ZipLine.PLAYER_OPERATIONS[event.player] = locations
            // メッセージを表示
            event.player.sendMessage("${ZipLine.PLAYER_OPERATIONS.size}個目のルートを設定しました。")
            // 効果音を再生
            event.player.playSound(event.player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        } else {
            // if:すでに同じルートが保存されている場合エラーを表示してreturn
            event.player.sendMessage("${ChatColor.RED}ルートが重複しています。")
            return
        }
    }

    init {
        val itemMeta = this.itemMeta
        // アイテム識別用のタグを付与
        itemMeta?.persistentDataContainer?.set(ITEM_TYPE, PersistentDataType.STRING, ZIP_LINE_TYPE)
        // アイテム名を設定
        itemMeta?.setDisplayName("${ChatColor.RED}ジップラインクリエイター")
        this.itemMeta = itemMeta

        // プレイヤーがクリック操作を実行した際に呼び出される
        ZipLine.INSTANCE.registerEvent<PlayerInteractEvent> { event ->
            // メインハンドでクリックしていない場合はreturn(オフハンドなど)
            if(event.hand != EquipmentSlot.HAND) { return@registerEvent }
            // この棒を使用していない場合はreturn
            if(event.item?.itemMeta?.persistentDataContainer?.get(ITEM_TYPE, PersistentDataType.STRING) != ZIP_LINE_TYPE) { return@registerEvent }
            when(event.action) {
                // ブロックをクリックしていた場合は上のonClickBlock関数を実行
                Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_BLOCK -> { this.onClickBlock(event) }
                else -> {}
            }
        }
    }
}