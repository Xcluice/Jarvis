package com.xcluice.jarvis
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AssistService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

class AssistSession(c: Context) : VoiceInteractionSession(c) {
    override fun onShow(args: Bundle?, flags: Int) {
        super.onShow(args, flags)
        startAssistantActivity(Intent(context, MainActivity::class.java).putExtra("listen", true))
    }
}
