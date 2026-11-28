package com.shilapi.xcertplay.settings

import android.content.Context
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

object ConnectionSettingsSection {

    fun createMfiTargetChoice(
        context: Context,
        selected: MfiTarget,
        theme: SettingsTheme = SettingsTheme.OVERLAY,
        onSelected: (MfiTarget) -> Unit,
    ): SettingsWidgets.ChoiceRowResult<MfiTarget> = SettingsWidgets.createChoiceRow(
        context = context,
        label = context.getString(R.string.mfi_certificate_signing_target),
        options = listOf(
            MfiTarget.LOCAL to context.getString(R.string.local_offline),
            MfiTarget.USB_CH341 to context.getString(R.string.usb_ch341),
        ),
        selected = selected,
        theme = theme,
        onSelected = onSelected,
    )

    fun createWirelessCarPlayRow(
        context: Context,
        checked: Boolean,
        theme: SettingsTheme = SettingsTheme.OVERLAY,
        onChanged: (Boolean) -> Unit,
    ): SettingsWidgets.SwitchRowResult = SettingsWidgets.createSwitchRow(
        context = context,
        label = context.getString(R.string.wireless_carplay_2),
        description = context.getString(R.string.wireless_carplay_transport),
        checked = checked,
        theme = theme,
        contentDescription = context.getString(R.string.wireless_carplay_transport),
        labelSizeSp = if (theme.isOverlay) 20f else 18f,
        onChanged = onChanged,
    )

    fun createHotspotModeChoice(
        context: Context,
        selected: WirelessHotspotMode,
        theme: SettingsTheme = SettingsTheme.OVERLAY,
        onSelected: (WirelessHotspotMode) -> Unit,
    ): SettingsWidgets.ChoiceRowResult<WirelessHotspotMode> {
        // JAECOO: Built-in Jaecoo SoftAP + same-LAN only (no BYD car hotspot / Wi‑Fi Direct).
        val modes = com.shilapi.xcertplay.JaecooConnectionModesUi.offered(context).map { mode ->
            mode to context.getString(when (mode) {
                WirelessHotspotMode.JAECOO_BUILTIN -> R.string.settings_built_in_jaecoo
                WirelessHotspotMode.MANUAL -> R.string.settings_built_in_car_hotspot_recommended
                WirelessHotspotMode.WIFI_P2P -> R.string.wi_fi_p2p_5_ghz
                WirelessHotspotMode.EXISTING_WIFI -> R.string.existing_wifi_title
                else -> R.string.existing_wifi_title
            })
        }
        return SettingsWidgets.createChoiceRow(
            context = context,
            label = context.getString(R.string.wi_fi_session),
            options = modes,
            selected = com.shilapi.xcertplay.JaecooConnectionModesUi.coerce(context, selected),
            theme = theme,
            onSelected = onSelected,
        )
    }
}
