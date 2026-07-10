package com.shihuaidexianyu.money.domain.usecase

import kotlinx.coroutines.sync.Mutex

/** Serializes in-process account lifecycle operations that span more than one storage system. */
class AccountLifecycleCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withLifecycleLock(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
