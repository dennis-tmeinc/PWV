package com.tme_inc.pwv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Scroller
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_liveview.*

import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.Scanner

/**
 * Created by dennis on 24/05/16.
 */
open class PwViewActivity : Activity() {

    protected var m_screenLandscape = false
    protected var m_screendensity: Float = 0.toFloat()

    protected var m_channel: Int = 0
    protected var m_totalChannel: Int = 0

    // network connecting mode
    protected var m_connMode: Int = 0

    protected var m_screen: ViewGroup? = null

    protected var m_osd = arrayOfNulls<TextView>(16)

    protected var mPwProtocol: PWProtocol? = null        // Pw Connection

    // player support
    internal var mplayer: PWPlayer? = null
    protected var mstream: PWStream? = null
    protected var mTimeAnimator: TimeAnimator? = null
    protected var m_UIhandler: Handler? = null

    protected var savedScreenTimeout = 0

    private var m_req_hideui = false

    internal var m_keepon = false

    protected var sreenTimeout: Int
        get() {
            try {
                return Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            } catch (e: Settings.SettingNotFoundException) {
            } catch (e: SecurityException) {
            }

            return 0
        }
        set(ms) {
            try {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)
            } catch (e: SecurityException) {

            }

        }

    // Generic screen initialization
    // called in onCreate(), after setContentView
    protected fun setupScreen() {

        var i: Int

        val prefs = getSharedPreferences("pwv", 0)
        m_connMode = prefs.getInt("connMode", DvrClient.CONN_DIRECT)


        m_screen = layoutscreen

        val configuration = resources.configuration
        m_screenLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (m_screenLandscape) {
            // Hide the status bar.
            m_screen?.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        m_screendensity = metrics.density

        // PW protocol for status update
        mPwProtocol = PWProtocol()

        i = 0
        while (i < m_osd.size) {
            m_osd[i] = TextView(this)
            m_osd[i]?.setTextColor(-0x2f000001)
            m_screen?.addView(
                m_osd[i],
                1,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            m_osd[i]?.setTextSize(
                TypedValue.COMPLEX_UNIT_DIP,
                configuration.screenWidthDp.toFloat() / 32.0f
            )
            m_osd[i]?.setShadowLayer(2f, 0f, 0f, Color.BLACK)
            m_osd[i]?.setVisibility(View.INVISIBLE)
            i++
        }

        m_channel = channel
        m_totalChannel = m_channel + 1

        // Screen swip support
        m_screen?.setOnTouchListener(object : View.OnTouchListener {

            internal var scroll_dir: Int = 0      // 0: touch down, 1: scrolling  2: volume

            internal var scroller = Scroller(baseContext)
            internal val scrollUpdate: Runnable = object : Runnable {
                override fun run() {
                    val sw = m_screen!!.width
                    var sx = m_screen!!.scrollX
                    if (sx > sw || sx < -sw) {
                        scroller.forceFinished(true)
                    }
                    if (scroller.computeScrollOffset()) {
                        m_screen!!.scrollTo(scroller.currX, 0)
                        m_screen!!.postOnAnimation(this)
                    } else {
                        sx = m_screen!!.scrollX
                        if (sx > sw / 2) {
                            goNextChannel()
                        } else if (sx < -sw / 2) {
                            goPrevChannel()
                        }
                    }
                }
            }

            internal var gestureDetector =
                GestureDetector(baseContext, object : GestureDetector.SimpleOnGestureListener() {

                    internal var disty: Float = 0.toFloat()

                    override fun onDown(e: MotionEvent): Boolean {
                        scroll_dir = 0
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        toggleUI()
                        return true
                    }

                    override fun onScroll(
                        e1: MotionEvent,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        if (scroll_dir == 0 && m_screen!!.scrollX == 0) {
                            if (Math.abs(distanceX) > Math.abs(distanceY)) {
                                scroll_dir = 1
                            } else {
                                scroll_dir = 2
                                disty = 0f
                            }
                        }

                        if (scroll_dir == 1) {
                            m_screen?.scrollBy(distanceX.toInt(), 0)
                        } else if (scroll_dir == 2 && mplayer != null) {      // 2 : set volume
                            disty += distanceY
                            if (disty > m_screendensity * 8) {
                                mplayer!!.adjustVolume(true)
                                disty = 0f
                            } else if (disty < -m_screendensity * 8) {
                                mplayer!!.adjustVolume(false)
                                disty = 0f
                            }
                        }

                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (scroll_dir == 1) {
                            fling(velocityX.toInt())
                            return true
                        }
                        return false
                    }
                })

            internal fun fling(velocityX: Int) {
                val screenWidth = m_screen!!.width
                scroller.fling(
                    m_screen!!.scrollX,
                    m_screen!!.scrollY,
                    -velocityX / 2,
                    0,
                    -5 * screenWidth,
                    5 * screenWidth,
                    0,
                    0
                )
                val dx = scroller.finalX
                if (dx > -screenWidth && dx <= -screenWidth / 2) {
                    scroller.finalX = -screenWidth
                } else if (dx < screenWidth && dx >= screenWidth / 2) {
                    scroller.finalX = screenWidth
                } else if (dx > -screenWidth / 2 && dx < screenWidth / 2) {
                    scroller.finalX = 0
                }
                if (scroller.duration < 300) {
                    scroller.extendDuration(500 - scroller.duration)
                }
                m_screen?.postOnAnimation(scrollUpdate)
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val res = gestureDetector.onTouchEvent(event)
                if (!res) {
                    val act = event.actionMasked
                    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                        val sx = m_screen!!.scrollX
                        if (sx != 0) {
                            fling(0)
                            return true
                        }
                    }

                }
                return res
            }
        })

    }

    protected open fun showUI() {
        // display action bar and menu
        if (m_screenLandscape) {

            actionBar!!.show()
            findViewById<View>(R.id.pwcontrol).visibility = View.VISIBLE
            findViewById<View>(R.id.pwcontrol).animate().alpha(1.0f)
            findViewById<View>(R.id.btPlayMode).animate().alpha(1.0f)

            if (m_UIhandler != null) {
                m_UIhandler!!.removeMessages(MSG_UI_HIDE) // remove pending hide ui
                m_UIhandler!!.sendEmptyMessageDelayed(MSG_UI_HIDE, 30000)
                m_req_hideui = true
            }

        }

    }

    protected open fun hideUI() {
        // display action bar and menu
        if (m_screenLandscape && actionBar!!.isShowing) {

            // Hide the status bar.
            m_screen?.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

            // Hide Title bar
            actionBar!!.hide()

            findViewById<View>(R.id.btPlayMode).animate()
                .alpha(0.0f)
            // Hide Buttons
            findViewById<View>(R.id.pwcontrol).animate()
                .alpha(0.0f)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        if (actionBar!!.isShowing) {
                            findViewById<View>(R.id.pwcontrol).visibility = View.VISIBLE
                        } else {
                            findViewById<View>(R.id.pwcontrol).visibility = View.GONE
                        }
                    }
                })

            m_req_hideui = false
        }
    }

    private fun toggleUI() {
        // display action bar and menu
        if (m_screenLandscape && actionBar!!.isShowing) {
            hideUI()
        } else {
            showUI()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (m_req_hideui && m_UIhandler != null) {
            m_UIhandler!!.removeMessages(MSG_UI_HIDE) // remove pending hide ui
            m_UIhandler!!.sendEmptyMessageDelayed(MSG_UI_HIDE, 30000)
            m_req_hideui = true
        }
    }

    protected fun screen_KeepOn(on: Boolean) {
        if (on) {
            if (!m_keepon) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                m_keepon = true
            }
        } else {
            if (m_keepon) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                m_keepon = false
            }
        }
    }

    protected fun stopMedia() {
        if (mstream != null) {
            mstream!!.release()
            mstream = null
        }
        if (mplayer != null) {
            mplayer!!.Release()
            mplayer = null
        }
        m_screen?.scrollTo(0, 0)
    }

    protected fun goPrevChannel() {
        if (mstream != null) {
            m_totalChannel = mstream!!.totalChannels
        }
        if (m_totalChannel < 1) m_totalChannel = 1
        m_channel--
        if (m_channel < 0) {
            m_channel = m_totalChannel - 1
        }
        stopMedia()
    }

    protected fun goNextChannel() {
        if (mstream != null) {
            m_totalChannel = mstream!!.totalChannels
        }
        if (m_totalChannel < 1) m_totalChannel = 1
        m_channel++
        if (m_channel >= m_totalChannel) {
            m_channel = 0
        }
        stopMedia()
    }

    protected open fun onAnimate(totalTime: Long, deltaTime: Long) {
        if (m_connMode == DvrClient.CONN_USB && mstream != null && !mstream!!.isActive) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        savedScreenTimeout = sreenTimeout
        if (savedScreenTimeout <= 0) {
            savedScreenTimeout = 60000
        }
        if (savedScreenTimeout > 1800000) {
            savedScreenTimeout = 1800000
        }
        sreenTimeout = 15000

        // force turn on screen keep on
        m_keepon = false
        screen_KeepOn(true)

        showUI()

        // start animator
        if (mTimeAnimator == null) {
            mTimeAnimator = TimeAnimator()
            mTimeAnimator!!.setTimeListener { timeAnimator, totalTime, deltaTime ->
                onAnimate(
                    totalTime,
                    deltaTime
                )
            }
        }
        mTimeAnimator!!.start()
    }

    override fun onPause() {
        super.onPause()

        mTimeAnimator!!.end()
        stopMedia()
        mPwProtocol?.cancel()

        sreenTimeout = savedScreenTimeout
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !m_screenLandscape) {
            var w = m_screen!!.width
            var h = m_screen!!.height
            if (w > 0 && h > 0) {
                val lp = m_screen!!.layoutParams as RelativeLayout.LayoutParams
                // get layout width&height
                w += lp.leftMargin + lp.rightMargin
                h += lp.topMargin + lp.bottomMargin
                if (h * 4 > w * 3) {
                    h = (h - w * 3 / 4) / 2
                    lp.setMargins(0, h, 0, h)
                    m_screen?.requestLayout()
                }
            }
        }
    }

    protected fun displayOSD(txtFrame: MediaFrame?) {

        if (txtFrame == null)
            return

        val frame = txtFrame.array
        var text_off = txtFrame.pos
        val text_len = text_off + txtFrame.len

        var idx = 0
        while (idx < m_osd.size &&
            m_osd[idx] != null &&
            text_off < text_len &&
            frame[text_off] == 's'.toByte() &&
            frame[text_off + 1] == 't'.toByte()
        ) {
            val osdLen = frame[text_off + 6].toInt()
            if (osdLen >= 4) {
                if (m_osd[idx]?.getVisibility() != View.VISIBLE) {
                    m_osd[idx]?.setVisibility(View.VISIBLE)

                    val align = frame[text_off + 8].toInt()
                    val posx = frame[text_off + 9].toInt()
                    val posy = frame[text_off + 10].toInt()

                    val h = m_osd[idx]!!.getHeight() / 24.0f
                    val lp = m_osd[idx]!!.getLayoutParams() as FrameLayout.LayoutParams
                    lp.height = -2
                    lp.width = -2
                    lp.gravity = 0

                    if (align and 1 != 0) {       // ALIGN LEFT
                        lp.leftMargin = posx * 2
                        lp.rightMargin = 0
                        lp.gravity = lp.gravity or Gravity.LEFT
                    } else if (align and 2 != 0) {       // ALIGN RIGHT
                        lp.leftMargin = 0
                        lp.rightMargin = posx * 2
                        lp.gravity = lp.gravity or Gravity.RIGHT
                    }
                    if (align and 4 != 0) {       // ALIGN TOP
                        lp.topMargin = (posy * h).toInt()
                        lp.bottomMargin = 0
                        lp.gravity = lp.gravity or Gravity.TOP
                    } else if (align and 8 != 0) {       // ALIGN BOTTOM
                        lp.topMargin = 0
                        lp.bottomMargin = (posy * h).toInt()
                        lp.gravity = lp.gravity or Gravity.BOTTOM
                    }
                    m_osd[idx]?.requestLayout()

                }

                try {
                    m_osd[idx]?.setText(
                        String(
                            frame,
                            text_off + 12,
                            osdLen - 4,
                            Charsets.ISO_8859_1     // Use ISO_8859 for degree symble
                        ).trim())
                } catch (e: UnsupportedEncodingException) {
                }

                text_off += osdLen + 8

            } else {
                break
            }
            idx++
        }
    }

    companion object {

        // app variables
        var tb_pos: Long = 0
        var tb_width = 3600
        var channel = 0
    }

}
