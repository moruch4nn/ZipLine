package dev.mr3n.zipline

import dev.moru3.minepie.Executor.Companion.runTask
import dev.moru3.minepie.Executor.Companion.runTaskTimer
import dev.moru3.minepie.Executor.Companion.runTaskTimerAsync
import dev.moru3.minepie.events.EventRegister.Companion.registerEvent
import dev.mr3n.zipline.commands.ZipLineCommand
import dev.mr3n.zipline.items.ZipLineCreator
import dev.mr3n.zipline.nms.nmsSetPositionRotation
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class ZipLine: JavaPlugin() {

    private val taskIdKey = NamespacedKey(this, "TASK_ID")

    // このリストに入っているプレイヤーはジップラインに乗れなくなります。
    private val ignorePlayers = mutableListOf<Player>()

    private val armorStands = mutableSetOf<ArmorStand>()

    override fun onEnable() {
        INSTANCE = this

        ZipLineCreator

        saveDefaultConfig()

        Bukkit.getPluginCommand("zipline")?.also { cmd ->
            cmd.tabCompleter = ZipLineCommand
            cmd.setExecutor(ZipLineCommand)
        }

        // ジップラインを作成する際のパーティクル表示
        this.runTaskTimer(3, 3) {
            // 作成中のルートを取得
            PLAYER_OPERATIONS.forEach { (player, locations) ->
                if(locations.size <= 1) { return@forEach }
                // ここで最初のLocationと次のLocationのペアを作成するために-1してrepeat
                genPath(locations, 3).forEach { location -> player.world.spawnParticle(Particle.REDSTONE, location, 1, .0, .0, .0, Particle.DustOptions(Color.RED, 1f)) }
            }
        }

        // 動いたプレイヤーを格納する。(PlayerMoveEvent)
        val queuePlayers = mutableSetOf<Player>()

        this.runTaskTimerAsync(1L,1L) {
            val removeStands = mutableSetOf<ArmorStand>()
            this.armorStands.forEach { armorStand ->
                if(armorStand in removeStands) { return@forEach }
                // removeStands.addAll(this.armorStands.filter{it!=armorStand}.filter{it.location.distance(armorStand.location)<2})
            }
            this.armorStands.removeAll(removeStands)
            removeStands.forEach { armorStand ->
                this.runTask { armorStand.remove() }
            }
        }

        // なんか重そうだから非同期で実行
        this.runTaskTimerAsync(2L, 2L) {
            val near = mutableSetOf<Player>()
            ZIP_LINES_ROUGH.forEach w@{ (world, zip) -> zip.forEach { (name, roughnessRoute) ->
                queuePlayers.forEach q@{ player ->
                    if(player.gameMode == GameMode.SPECTATOR) { return@q }
                    if(player.world.name!=world) { return@w }
                    // ワールド内のすべてのジップラインを取得
                    // ジップラインのルート
                    val keys = roughnessRoute.keys.sortedBy { it.distance(player.location) }
                    if(keys.first().distance(player.location) > 30) { return@q }
                    val first = roughnessRoute[keys.first()]?.find { it.distance(player.location) < 1 }
                    if(first!=null) {
                        near.add(player)
                        // ignorePlayersに入っている場合はreturn
                        //Q.なんで一番上に書かないの？ A.ジップラインから離れた際にignorePlayersからプレイヤーを消す処理が必要だから。
                        if(ignorePlayers.contains(player)) { return@q }
                        ignorePlayers.add(player)
                        // ジップラインのルート(軽量化用の荒いやつじゃなくてちゃんとしたやつ)
                        val route = ZIP_LINES[world]!![name]!!
                        val index = route.indexOf(first)
                        // プレイヤーが触れた場所
                        val loc = route[index]
                        // プレイヤーの触れた場所から一個進んだ場所
                        val front = route.getOrNull(index+1)
                        // プレイヤーの触れた場所から一個戻った場所
                        val back = route.getOrNull(index-1)
                        if(back==null) {
                            // if:これ以上戻れない場合は進める方向(前)に進む
                            runTask { player.rideZip(route.subList(index,route.size)) }
                        } else if(front==null) {
                            // if:これ以上進めない場合は進める方向(後)に進む
                            runTask { player.rideZip(route.subList(0,index).reversed()) }
                        } else {
                            // 現在の位置から次の場所の方向ベクトル(ノーマライズ済み)
                            val frontDir = front.toVector().subtract(loc.toVector()).setY(0).normalize()
                            // 現在の位置から前の場所の方向ベクトル(ノーマライズ済み)
                            val backDir = back.toVector().subtract(loc.toVector()).setY(0).normalize()
                            // 前方向の方向ベクトル(ノーマライズ済み)からプレイヤーの方向ベクトル(ノーマライズ済み)を引いたやつ
                            val frontLength = frontDir.subtract(player.location.direction.setY(0).normalize()).length()
                            // 後ろ方向の方向ベクトル(ノーマライズ済み)からプレイヤーの方向ベクトル(ノーマライズ済み)を引いたやつ
                            val backLength = backDir.subtract(player.location.direction.setY(0).normalize()).length()
                            if(frontLength < backLength) {
                                // if:前方向のベクトルが短い場合はプレイヤーが前気味に向いているので前に進める
                                runTask { player.rideZip(route.subList(index,route.size)) }
                            } else {
                                // if:前方向のベクトルが短い場合はプレイヤーが後気味に向いているので前に進める
                                runTask { player.rideZip(route.subList(0,index).reversed()) }
                            }
                        }
                    }
                }

            } }
            ignorePlayers.removeAll(queuePlayers.filterNot { near.contains(it) })
            queuePlayers.clear()
        }

        // >>> PlayerMoveEvent >>>
        // プレイヤーが動いた際に実行される。
        this.registerEvent<PlayerMoveEvent> { event ->
            // プレイヤーがすでに何かに座っている場合はreturn
            if(event.player.vehicle!=null) { return@registerEvent }
            queuePlayers.add(event.player)
        }
        // <<< PlayerMoveEvent <<<

        reload()
    }

    private fun Player.rideZip(route: List<Location>) {
        val player = this
        val first = route.first()
        val last = route.last()
        // ジップラインのワールド
        val world = first.world?:return
        // 座らせるためのアーマースタンドを生成
        val armorStand = world.spawn(first, ArmorStand::class.java)
        // 動かないように設定
        armorStand.setAI(false)
        // 当たり判定を消す
        armorStand.isMarker = true
        // 透明にする
        armorStand.isInvisible = true
        // プレイヤーを乗せる
        armorStand.addPassenger(player)
        this@ZipLine.armorStands.add(armorStand)
        // アーマースタンドを動かすタスク
        var bukkitTask: BukkitTask? = null
        val iterator = route.iterator()
        bukkitTask = this@ZipLine.runTaskTimer(1, 1) {
            if(armorStand.isDead||armorStand.passengers.isEmpty()) {
                // if:アーマースタンドからプレイヤーが降りた場合は削除
                bukkitTask?.cancel()
                armorStand.remove()
            } else {
                // if:アーマスタンドにプレイヤーが載っている場合
                if (iterator.hasNext()) {
                    // if:ルートがまだ残っている場合
                    // 次のLocationを取得
                    val next = iterator.next()
                    // アーマースタンドを移動させる。
                    armorStand.nmsSetPositionRotation(next.x, next.y, next.z, 0f, 0f)

                    // >>> 速度を出すために次のイテレーターをチョット進めておく。 >>>
                    // -1.0 ~ 1.0
                    // val gradient = (next.y - old.y) * 10.0
                    // 0.0 - 12.0
                    // val speed = (1.0-((gradient+1.0)/2.0)) * 8.0
                    // repeat(maxOf(0, speed.toInt() - 1)) { if (iterator.hasNext()) { old =  iterator.next() } }
                    // <<< 速度を出すために次のイテレーターをチョット進めておく。 <<<

                    // いい感じの係数が見つからなかったため等速にしています。
                    repeat(4) { if (iterator.hasNext()) { iterator.next() } }
                } else {
                    // if:ルートがなくなった場合
                    // アーマースタンドを最終地点にテレポート
                    armorStand.nmsSetPositionRotation(last.x, last.y, last.z, 0f, 0f)
                    bukkitTask?.cancel()
                    armorStand.remove()
                    // 降りた際にすぐにジップラインが反応しないようにする
                    ignorePlayers.add(player)
                }
            }
        }
        // アーマースタンドにタスクIDを付与する
        armorStand.persistentDataContainer.set(taskIdKey, PersistentDataType.INTEGER, bukkitTask.taskId)
    }

    private fun genPath(locations: List<Location>, roughness: Int): List<Location> {
        if(locations.isEmpty()) { return listOf() } else if(locations.size < 2) { return listOf(locations.first()) }
        val result = mutableListOf<Location>()
        repeat(locations.size-1) { index ->
            // from
            val loc1 = locations[index].clone()
            // to
            val loc2 = locations[index+1].clone()
            // パーティクルの生成位置(動的)
            val location = loc1.clone()
            // fromからtoまでの距離
            val distance = loc1.distance(loc2)
            // fromからtoへの方向ベクトル(長さ0.4)
            val direction = loc2.toVector().subtract(loc1.toVector()).normalize().multiply(0.1 * roughness)
            // fromとlocationがdistanceを超えるまでwhiteでループ
            while(loc1.distance(location) < distance) {
                // locationに長さベクトルを追加
                location.add(direction)
                result.add(location.clone())
            }
        }
        return result
    }

    fun reload() {
        reloadConfig()
        // >> 下の文を短く記述するために関数を作成 >>
        fun keys(path: String) = config.getConfigurationSection(path)?.getKeys(false)?: setOf()
        fun <K, V> Iterable<K>.aw(valueSelector: (K) -> V): Map<K, V> = this.associateWith(valueSelector)
        // << 下の文を短く記述するために関数を作成 <<
        // configからzipLineの一覧を取得し変数に格納
        ZIP_LINES = keys("lines").aw { w -> keys("lines.${w}").aw { z -> genPath(keys("lines.${w}.${z}").map { index -> config.getLocation("lines.${w}.${z}.${index}")!! },1) } }
        ZIP_LINES_ROUGH = ZIP_LINES.mapValues { it.value.mapValues {t->t.value.chunked(100).associateBy {i->i[i.size/2]}.mapValues {f->f.value.filterIndexed{i,_->i%4==0} } } }
    }

    companion object {
        // すべてのジップライン一覧
        var ZIP_LINES = mapOf<String,Map<String, List<Location>>>()
            private set

        var ZIP_LINES_ROUGH = mapOf<String,Map<String, Map<Location,List<Location>>>>()
            private set

        // プラグインインスタンス
        lateinit var INSTANCE: ZipLine

        // 作成中のルート保管用変数
        val PLAYER_OPERATIONS = mutableMapOf<Player, MutableList<Location>>()
    }
}