package com.yourstudy.app.ui

import com.yourstudy.app.Popups
import com.yourstudy.app.RemoteControlConfig

/** Gate for any control.json-driven tool entry: intercept popup > lock notice > open. */
fun requestFeature(cfg: RemoteControlConfig, feature: String, uiId: String, title: String, onOpen: () -> Unit) {
    val ui = cfg.ui(uiId)
    val f = cfg.feature(feature)
    val intercept = ui.interceptPopupId
    when {
        intercept != null -> Popups.show(intercept)
        !f.enabled || f.locked -> Popups.notice(title, f.lockReason)
        else -> onOpen()
    }
}
