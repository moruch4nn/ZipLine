package dev.mr3n.zipline.nms

import org.bukkit.entity.Entity

private fun Entity.getHandle(): Any = this::class.java.getMethod("getHandle").invoke(this)
fun Entity.nmsSetPositionRotation(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
    val handle = this.getHandle()
    val method = handle::class.java.getMethod("b", Double::class.java, Double::class.java, Double::class.java, Float::class.java, Float::class.java)
    method.invoke(handle, x, y, z, yaw, pitch)
}