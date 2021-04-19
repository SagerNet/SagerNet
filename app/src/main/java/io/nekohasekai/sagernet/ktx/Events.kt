package io.nekohasekai.sagernet.ktx

import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

interface EventListener {
    fun onEvent(eventId: Int, vararg args: Any)
}

const val EVENT_UPDATE_PROFILE = 0
const val EVENT_UPDATE_GROUP = EVENT_UPDATE_PROFILE + 1

private val listeners = ConcurrentHashMap<Int, LinkedList<SoftReference<EventListener>>>()

fun registerListener(eventId: Int, listener: EventListener) {
    listeners.getOrPut(eventId) { LinkedList() }.add(SoftReference(listener))
}

fun removeListener(eventId: Int, listener: EventListener) {
    listeners[eventId]?.removeIf { it.get() == listener }
}

fun postNotification(eventId: Int, vararg args: Any) {
    listeners[eventId]?.apply {
        val iterator = iterator()
        while (iterator.hasNext()) {
            val listener = iterator.next().get()
            if (listener == null) {
                iterator.remove()
                continue
            }
            listener.onEvent(eventId, * args)
        }
    }
}