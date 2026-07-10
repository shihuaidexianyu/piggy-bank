package com.shihuaidexianyu.money.domain.usecase

import java.util.UUID

internal fun newLedgerOperationId(): String = UUID.randomUUID().toString()
