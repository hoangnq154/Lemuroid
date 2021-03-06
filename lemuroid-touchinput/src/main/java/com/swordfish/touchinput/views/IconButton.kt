package com.swordfish.touchinput.views

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton
import com.jakewharton.rxrelay2.PublishRelay
import com.swordfish.touchinput.events.ViewEvent
import com.swordfish.touchinput.interfaces.ButtonEventsSource
import io.reactivex.Observable

class IconButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr), ButtonEventsSource {

    private val events: PublishRelay<ViewEvent.Button> = PublishRelay.create()

    init {
        setOnTouchListener { _, event -> handleTouchEvent(event) }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                events.accept(ViewEvent.Button(KeyEvent.ACTION_DOWN, 0, true))
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                events.accept(ViewEvent.Button(KeyEvent.ACTION_UP, 0, false))
            }
        }
        return true
    }

    override fun getEvents(): Observable<ViewEvent.Button> = events
}
