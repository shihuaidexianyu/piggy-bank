package com.shihuaidexianyu.money

import android.app.Application

class MoneyApplication : Application() {
    lateinit var container: MoneyAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = MoneyAppContainer(this)
    }
}
