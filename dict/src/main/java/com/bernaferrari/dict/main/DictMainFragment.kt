package com.bernaferrari.dict.main

import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.mvrx.activityViewModel
import com.bernaferrari.base.mvrx.MvRxEpoxyController
import com.bernaferrari.base.mvrx.simpleController
import com.bernaferrari.dict.GifMainCarouselBindingModel_
import com.bernaferrari.dict.R
import com.bernaferrari.dict.extensions.getScreenPercentSize
import com.bernaferrari.dict.extensions.openBrowserItemHandler
import com.bernaferrari.dict.extensions.shareItemHandler
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.snackbar.Snackbar
import com.yarolegovich.discretescrollview.DiscreteScrollView
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.gif_frag_main.*
import java.util.concurrent.TimeUnit

class DictMainFragment : DictBaseMainFragment(),
    DiscreteScrollView.OnItemChangedListener<RecyclerView.ViewHolder> {

    private val viewModel: DictViewModel by activityViewModel()

    private var currentIdSelected = ""

    override fun epoxyController(): MvRxEpoxyController = simpleController(viewModel) { state ->

        //        viewModel.showErrorMessage.accept(state.items is Fail)

        val itemHeight = requireActivity().getScreenPercentSize()

        state.fullList.forEach {
            GifMainCarouselBindingModel_()
                .id(it.gifId)
                .gifId(it.gifId)
                .onClick { _, _, _, position ->
                    recyclerDiscrete.smoothScrollToPosition(position)
                }
                .customWidth(itemHeight)
                .addTo(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerDiscrete.addOnItemChangedListener(this)

        val bts = BottomSheetBehavior.from(bottomSheet)

        disposableManager += viewModel.idSelected.subscribe {
            bts.state = STATE_COLLAPSED
            val nextPosition = viewModel.fullList.indexOfFirst { fir -> fir.gifId == it }
            recyclerDiscrete.smoothScrollToPosition(nextPosition) // position becomes selected with animated scroll
        }

        shareContent.setOnClickListener { _ ->
            val item = viewModel.fullList.first { it.gifId == currentIdSelected }
            requireActivity().shareItemHandler(item.title, "https://gfycat.com/${item.gifId}")
        }

        shareContent.setOnLongClickListener {
            it.context.openBrowserItemHandler("https://gfycat.com/$currentIdSelected")
            true
        }

        viewModel.selectSubscribe(DictState::isLoading) {
            itemsProgress.isVisible = it
        }

        disposableManager += viewModel.showErrorMessage
            .observeOn(AndroidSchedulers.mainThread())
            .skipWhile { !it }
            .subscribe {
                Snackbar.make(recyclerDiscrete, R.string.gif_error, Snackbar.LENGTH_LONG).show()
            }
    }

    override fun onCurrentItemChanged(viewHolder: RecyclerView.ViewHolder?, adapterPosition: Int) {
        // starts the timer to show a loading bar in case the video is not loaded fast
        showProgressIfNecessary()

        // if user scrolls before loading finishes, position changes and things get weird
        if (adapterPosition < 0 && viewModel.fullList.isNotEmpty()) {
            recyclerDiscrete.scrollToPosition(0)
            return
        }

        // if viewModel is empty, hide everything
        if (viewModel.fullList.isEmpty()) {
            isVideoShown = false
            card.isVisible = false
            return
        }

        val item = viewModel.fullList[adapterPosition]

        video_view?.setVideoURI("https://thumbs.gfycat.com/${item.gifId}-mobile.mp4".toUri())
        titleContent?.text = item.title // might be null on split screen
        currentIdSelected = item.gifId

        viewModel.itemSelectedRelay.accept(adapterPosition)
    }

    override fun onStart() {
        super.onStart()
        video_view.start()
    }

    override fun onStop() {
        video_view.pause()
        super.onStop()
    }

    /*
        This will start a 500ms timer. If the GIF is not loaded in this period, the progress bar will
        be shown, so the user knows it is taking longer than expected but it is still loading.
        */
    private fun showProgressIfNecessary() {
        progressDisposable?.dispose()
        progressDisposable =
            Completable.timer(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe { progressBar?.show() }
    }
}
