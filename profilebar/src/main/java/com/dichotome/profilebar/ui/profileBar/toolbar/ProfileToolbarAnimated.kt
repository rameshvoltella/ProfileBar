package com.dichotome.profilebar.ui.profileBar.toolbar

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import com.dichotome.profilebar.R
import com.dichotome.profilebar.ui.ProfileOptionWindow
import com.dichotome.profilebar.ui.ProfileTabLayout
import com.dichotome.profilebar.ui.profileBar.ProfileBar
import com.dichotome.profilebar.util.anim.*
import com.dichotome.profilephoto.ui.ZoomingImageView
import com.dichotome.profileshared.anim.AnimationHelper
import com.dichotome.profileshared.anim.DecelerateAccelerateInterpolator
import com.dichotome.profileshared.extensions.addTo
import com.dichotome.profileshared.extensions.cancelAll
import com.dichotome.profileshared.extensions.evaluateAll
import com.dichotome.profileshared.views.CircularImageView
import com.google.android.material.appbar.AppBarLayout

class ProfileToolbarAnimated @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ProfileToolbar(context, attrs, defStyle),
    AppBarLayout.OnOffsetChangedListener {

    companion object {
        const val TAG = "ProfileToolbarAnimated"

        private const val DURATION = 550L
        private const val DURATION_MEDIUM = (0.95 * DURATION).toLong()
        private const val DURATION_SHORT = (0.9 * DURATION).toLong()
        private const val DURATION_SHORTER = (0.85 * DURATION).toLong()

        private const val SUPER_STATE = "superState"
        private const val FRAME_BACKGROUND_ALPHA = "frameBackgroundAlpha"
        private const val TOOLBAR_OPEN = "toolbarOpen"
        private const val DIM_ALPHA = "dimAlpha"
        private const val DIM_HEIGHT = "dimHeight"
        private const val TOP_MARGIN = "wallpaper_margin"

        private var TRANSITION_THRESHOLD: Float = 0.5f
    }
    private var wallpaperImageVisible: Boolean = wallpaperImage.isVisible
    private var dimViewVisible: Boolean = dimView.isVisible
    private var bottomGlowViewVisible: Boolean = bottomGlowView.isVisible
    private var photoImageVisible: Boolean = photoImage.isVisible
    private var photoFrameBackgroundVisible: Boolean = photoFrameBackground.isVisible
    private var photoFrameVisible: Boolean = photoFrame.isVisible
    private var titleTvVisible: Boolean = photoFrame.isVisible
    private var editTitleVisible: Boolean = editTitle.isVisible
    private var subtitleTvVisible: Boolean = subtitleTV.isVisible
    private var optionButtonVisible: Boolean = optionButton.isVisible
    private var followButtonVisible: Boolean = followButton.isVisible
    private var tabsVisible: Boolean = tabs.isVisible

    private var lastPosition = 0
    private var toolbarOpen = true
    private var constraintsChanged = false
    private var dimAdjusted = false
    private var dimHeight = -1f
    private var dimCollapsedAlpha = 0.7f
    private var animatorsInitialised = false

    private lateinit var appBar: ProfileBar
    private var appBarHeight = 0f

    private var collapseAnimators = ArrayList<AnimationHelper>()

    private lateinit var translationName: AnimationHelper
    private lateinit var translationJoinedOn: AnimationHelper
    private lateinit var translationFrame: AnimationHelper
    private lateinit var alphaPhoto: AnimationHelper
    private lateinit var scalePhoto: AnimationHelper
    private lateinit var rotationOptionButton: AnimationHelper
    private lateinit var alphaDim: AnimationHelper

    private lateinit var alphaPhotoFrameBackground: AnimationHelper

    private val openConstraintSet = ConstraintSet()
    private val collapsedConstraintSet = ConstraintSet()

    init {
        id = R.id.toolbar_animated
        initFrameAnimOnZoom()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (parent is ProfileBar) {
            appBar = parent as ProfileBar
            appBar.addOnOffsetChangedListener(this)

            openConstraintSet.clone(context, R.layout.toolbar_profile)
            collapsedConstraintSet.clone(context, R.layout.toolbar_profile_collapsed)

            defineConstraints()
        }
    }

    private fun saveVisibility() {
        wallpaperImageVisible = wallpaperImage.isVisible
        dimViewVisible = dimView.isVisible
        bottomGlowViewVisible = bottomGlowView.isVisible
        photoImageVisible = photoImage.isVisible
        photoFrameBackgroundVisible = photoFrameBackground.isVisible
        photoFrameVisible = photoFrame.isVisible
        titleTvVisible = photoFrame.isVisible
        editTitleVisible = editTitle.isVisible
        subtitleTvVisible = subtitleTV.isVisible
        optionButtonVisible = optionButton.isVisible
        followButtonVisible = followButton.isVisible
        tabsVisible = tabs.isVisible
    }

    private fun restoreVisibility() {
        wallpaperImage.isVisible = wallpaperImageVisible
        dimView.isVisible = dimViewVisible
        bottomGlowView.isVisible = bottomGlowViewVisible
        photoImage.isVisible = photoImageVisible
        photoFrameBackground.isVisible = photoFrameBackgroundVisible
        photoFrame.isVisible = photoFrameVisible
        photoFrame.isVisible = titleTvVisible
        editTitle.isVisible = editTitleVisible
        subtitleTV.isVisible = subtitleTvVisible
        optionButton.isVisible = optionButtonVisible
        followButton.isVisible = followButtonVisible
        tabs.isVisible = tabsVisible
    }

    private fun defineConstraints() =
        if (toolbarOpen)
            applyToolbarOpen()
        else
            applyToolbarCollapsed()


    private fun applyToolbarOpen() {
        saveVisibility()
        openConstraintSet.applyTo(this)
        restoreVisibility()

        toolbarOpen = true
    }

    private fun applyToolbarCollapsed() {
        saveVisibility()
        collapsedConstraintSet.applyTo(this)
        restoreVisibility()

        dimView.alpha = dimCollapsedAlpha
        toolbarOpen = false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (constraintsChanged) {
            dimAdjusted = false
            collapseAnimators.evaluateAll()

            constraintsChanged = false
        }

        if (appBar.measuredHeight > 0) {
            if (!dimAdjusted) {
                appBarHeight = appBar.measuredHeight.toFloat()
                if (dimHeight == -1f)
                    dimHeight = tabs.measuredHeight + appBarHeight / 3

                TRANSITION_THRESHOLD = 1f - (dimHeight / appBarHeight)

                dimView.layoutParams = dimView.layoutParams.apply {
                    height = dimHeight.toInt()
                }
                minimumHeight = dimHeight.toInt()
                dimAdjusted = true
            }
        }
    }

    private fun initFrameAnimOnZoom() {
        alphaPhotoFrameBackground = SmoothAlphaAnimationHelper(
            photoFrameBackground,
            ZoomingImageView.DURATION_ZOOM
        ).apply {
            photoImage.setOnZoomListener {
                evaluate()
            }
        }
    }

    private fun initAnimators() {
        titleTV.let {
            translationName = HorizontalAnimationHelper(
                it,
                DecelerateInterpolator(),
                DURATION
            ).addTo(collapseAnimators)
        }

        subtitleTV.let {
            translationJoinedOn = HorizontalAnimationHelper(
                it,
                DecelerateInterpolator(),
                DURATION
            ).addTo(collapseAnimators)
        }

        photoFrame.let {
            translationFrame = PlainTranslationHelper(
                it,
                DecelerateInterpolator(),
                DURATION_SHORTER
            ).addTo(collapseAnimators)

            alphaPhoto = ReturnAlphaAnimationHelper(
                it,
                DecelerateAccelerateInterpolator(),
                DURATION_MEDIUM
            ).addTo(collapseAnimators)

            scalePhoto = ReturnScaleAnimationHelper(
                0.4f,
                it,
                DecelerateAccelerateInterpolator(),
                DURATION_MEDIUM
            ).addTo(collapseAnimators)
        }

        optionButton.let {
            rotationOptionButton = RotationAnimationHelper(
                it,
                OvershootInterpolator(),
                DURATION_SHORT
            ).addTo(collapseAnimators)
        }

        dimView.let {
            alphaDim = AlphaAnimationHelper(
                it,
                DecelerateInterpolator(),
                DURATION_SHORT,
                dimCollapsedAlpha, 1f
            ).addTo(collapseAnimators)
        }

        animatorsInitialised = true
    }

    private fun setTopMargin(value: Int) {
        layoutParams = (layoutParams as AppBarLayout.LayoutParams).apply {
            topMargin = value
        }
    }

    private fun initAnimatorsOpen() {
        if (!animatorsInitialised) initAnimators()
    }

    private fun initAnimatorsCollapsed() {
        val oldHeight = appBarHeight
        if (!animatorsInitialised) {
            appBar.layoutParams = appBar.layoutParams.apply {
                height = dimHeight.toInt()
            }
            initAnimators()
            appBar.layoutParams = appBar.layoutParams.apply {
                height = oldHeight.toInt()
            }
        }
    }

    override fun onOffsetChanged(appBar: AppBarLayout?, verticalOffset: Int) {
        appBar?.let {
            if (lastPosition == verticalOffset) {
                return
            }
            lastPosition = verticalOffset

            if (toolbarOpen) setTopMargin(-verticalOffset)

            val progress = Math.abs(verticalOffset / it.height.toFloat())

            if (toolbarOpen && progress >= TRANSITION_THRESHOLD) {
                initAnimatorsOpen()
                applyToolbarCollapsed()
                constraintsChanged = true

            } else if (!toolbarOpen && progress < TRANSITION_THRESHOLD) {
                initAnimatorsCollapsed()
                applyToolbarOpen()
                constraintsChanged = true
            }
        }
    }

    override fun onSaveInstanceState() = Bundle().apply {
        putParcelable(SUPER_STATE, super.onSaveInstanceState())

        putBoolean(TOOLBAR_OPEN, toolbarOpen)

        putFloat(DIM_HEIGHT, dimHeight)
        putFloat(DIM_ALPHA, dimView.alpha)

        putInt(TOP_MARGIN, marginTop)

        putFloat(FRAME_BACKGROUND_ALPHA, photoFrameBackground.alpha)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val superState = state?.let {
            it as Bundle

            toolbarOpen = it.getBoolean(TOOLBAR_OPEN)
            defineConstraints()

            dimHeight = it.getFloat(DIM_HEIGHT)
            dimView.alpha = it.getFloat(DIM_ALPHA)

            setTopMargin(it.getInt(TOP_MARGIN))

            photoFrameBackground.alpha = it.getFloat(FRAME_BACKGROUND_ALPHA)

            it.getParcelable<Parcelable>(SUPER_STATE)
        }
        super.onRestoreInstanceState(superState)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        collapseAnimators.cancelAll()
        alphaPhotoFrameBackground.cancel()
    }
}