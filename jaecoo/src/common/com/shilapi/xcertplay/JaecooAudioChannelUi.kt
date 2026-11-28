package com.shilapi.xcertplay

import android.content.Context

/**
 * On Jaecoo/Desay, media and navigation speakers are chosen by OEM audio policy
 * ([com.shilapi.xcertplay.media.DesayAudioAttributes] + DSP nav channel), not by DiPlay's
 * stream-type channel numbers. The Settings pickers are no-ops and confuse drivers — hide them.
 * Keep this gate in the jaecoo tree; DiPlayActivity only needs a one-line call site.
 */
object JaecooAudioChannelUi {
    /** Hide media/navigation "audio channel" number pickers (and their preview dialogs). */
    @Suppress("UNUSED_PARAMETER")
    fun hideChannelPickers(context: Context): Boolean = true
}
