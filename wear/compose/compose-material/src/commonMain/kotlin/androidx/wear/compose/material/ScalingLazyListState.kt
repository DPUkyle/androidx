/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.wear.compose.material

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Creates a [ScalingLazyListState] that is remembered across compositions.
 *
 * @param initialCenterItemIndex the initial value for [ScalingLazyListState.centerItemIndex],
 * defaults to 1. This will place the 2nd list item (index == 1) in the center of the viewport and
 * the first item (index == 0) before it.
 *
 * @param initialCenterItemScrollOffset the initial value for
 * [ScalingLazyListState.centerItemScrollOffset] in pixels
 */
@Composable
public fun rememberScalingLazyListState(
    initialCenterItemIndex: Int = 1,
    initialCenterItemScrollOffset: Int = 0
): ScalingLazyListState {
    return rememberSaveable(saver = ScalingLazyListState.Saver) {
        ScalingLazyListState(
            initialCenterItemIndex,
            initialCenterItemScrollOffset
        )
    }
}

/**
 * A state object that can be hoisted to control and observe scrolling.
 *
 * In most cases, this will be created via [rememberScalingLazyListState].
 *
 * @param initialCenterItemIndex the initial value for [ScalingLazyListState.centerItemIndex],
 * defaults to 1. This will place the 2nd list item (index == 1) in the center of the viewport and
 * the first item (index == 0) before it.
 *
 * If the developer wants custom control over position and spacing they can switch off autoCentering
 * and provide contentPadding.
 *
 * @param initialCenterItemScrollOffset the initial value for
 * [ScalingLazyListState.centerItemScrollOffset]
 *
 * Note that it is not always possible for the values provided by [initialCenterItemIndex] and
 * [initialCenterItemScrollOffset] to be honored, e.g. If [initialCenterItemIndex] is set to a value
 * larger than the number of items initially in the list, or to an index that can not be placed in
 * the middle of the screen due to the contentPadding or autoCentering properties provided to the
 * [ScalingLazyColumn]. After the [ScalingLazyColumn] is initially drawn the actual values for the
 * [centerItemIndex] and [centerItemScrollOffset] can be read from the state.
 */
@Stable
class ScalingLazyListState constructor(
    private var initialCenterItemIndex: Int = 1,
    private var initialCenterItemScrollOffset: Int = 0
) : ScrollableState {

    internal var lazyListState: LazyListState = LazyListState(0, 0)
    internal val extraPaddingPx = mutableStateOf<Int?>(null)
    internal val beforeContentPaddingPx = mutableStateOf<Int?>(null)
    internal val afterContentPaddingPx = mutableStateOf<Int?>(null)
    internal val scalingParams = mutableStateOf<ScalingParams?>(null)
    internal val gapBetweenItemsPx = mutableStateOf<Int?>(null)
    internal val viewportHeightPx = mutableStateOf<Int?>(null)
    internal val reverseLayout = mutableStateOf<Boolean?>(null)
    internal val anchorType = mutableStateOf<ScalingLazyListAnchorType?>(null)
    internal val autoCentering = mutableStateOf<AutoCenteringParams?>(null)
    internal val initialized = mutableStateOf<Boolean>(false)
    internal val localInspectionMode = mutableStateOf<Boolean>(false)

    // The following three are used together when there is a post-initialization incomplete scroll
    // to finish next time the ScalingLazyColumn is visible
    private val incompleteScrollItem = mutableStateOf<Int?>(null)
    private val incompleteScrollOffset = mutableStateOf<Int?>(null)
    private val incompleteScrollAnimated = mutableStateOf(false)

    /**
     * The index of the item positioned closest to the viewport center
     */
    public val centerItemIndex: Int
        get() =
            (layoutInfo as? DefaultScalingLazyListLayoutInfo)?.centerItemIndex
                ?: initialCenterItemIndex

    internal val topAutoCenteringItemSizePx: Int by derivedStateOf {
        if (extraPaddingPx.value == null || scalingParams.value == null ||
            gapBetweenItemsPx.value == null || viewportHeightPx.value == null ||
            anchorType.value == null || reverseLayout.value == null ||
            beforeContentPaddingPx.value == null || autoCentering.value == null ||
            autoCentering.value == null || layoutInfo.visibleItemsInfo.isEmpty()
        ) {
            0
        } else {
            (layoutInfo.beforeAutoCenteringPadding - gapBetweenItemsPx.value!!).coerceAtLeast(0)
        }
    }

    internal val bottomAutoCenteringItemSizePx: Int by derivedStateOf {
        if (extraPaddingPx.value == null || scalingParams.value == null ||
            gapBetweenItemsPx.value == null || viewportHeightPx.value == null ||
            anchorType.value == null || reverseLayout.value == null ||
            beforeContentPaddingPx.value == null || autoCentering.value == null ||
            layoutInfo.visibleItemsInfo.isEmpty()
        ) {
            0
        } else {
            (layoutInfo.afterAutoCenteringPadding - gapBetweenItemsPx.value!!).coerceAtLeast(0)
        }
    }

    /**
     * The offset of the item closest to the viewport center. Depending on the
     * [ScalingLazyListAnchorType] of the [ScalingLazyColumn] the offset will be relative to either
     * the items Edge or Center.
     */
    public val centerItemScrollOffset: Int
        get() = (layoutInfo as? DefaultScalingLazyListLayoutInfo)?.centerItemScrollOffset
            ?: initialCenterItemScrollOffset

    /**
     * The object of [ScalingLazyListLayoutInfo] calculated during the last layout pass. For
     * example, you can use it to calculate what items are currently visible.
     */
    public val layoutInfo: ScalingLazyListLayoutInfo by derivedStateOf {
        if (extraPaddingPx.value == null || scalingParams.value == null ||
            gapBetweenItemsPx.value == null || viewportHeightPx.value == null ||
            anchorType.value == null || reverseLayout.value == null ||
            beforeContentPaddingPx.value == null
        ) {
            EmptyScalingLazyListLayoutInfo
        } else {
            val visibleItemsInfo = mutableListOf<ScalingLazyListItemInfo>()
            val viewportHeightPx = viewportHeightPx.value!!
            var newCenterItemIndex = 0
            var newCenterItemScrollOffset = 0
            val visible = initialized.value || localInspectionMode.value

            // The verticalAdjustment is used to allow for the extraPadding that the
            // ScalingLazyColumn employs to ensure that there are sufficient list items composed
            // by the underlying LazyList even when there is extreme scaling being applied that
            // could result in additional list items be eligible to be drawn.
            // It is important to adjust for this extra space when working out the viewport
            // center-line based coordinate system of the ScalingLazyList.
            val verticalAdjustment =
                lazyListState.layoutInfo.viewportStartOffset + extraPaddingPx.value!!

            // Find the item in the middle of the viewport
            val centralItemArrayIndex =
                findItemNearestCenter(viewportHeightPx, verticalAdjustment)
            if (centralItemArrayIndex != null) {
                val originalVisibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
                val centralItem = originalVisibleItemsInfo[centralItemArrayIndex]

                // Place the center item
                val centerItemInfo: ScalingLazyListItemInfo = calculateItemInfo(
                    centralItem.offset,
                    centralItem,
                    verticalAdjustment,
                    viewportHeightPx,
                    scalingParams.value!!,
                    beforeContentPaddingPx.value!!,
                    anchorType.value!!,
                    autoCentering.value,
                    visible
                )
                visibleItemsInfo.add(
                    centerItemInfo
                )

                newCenterItemIndex = centerItemInfo.index
                newCenterItemScrollOffset = -centerItemInfo.offset

                // Find the adjusted position of the central item in the coordinate system of the
                // underlying LazyColumn by adjusting for any scaling
                val centralItemAdjustedUnderlyingOffset =
                    centralItem.offset + ((centerItemInfo.startOffset(anchorType.value!!) -
                        centerItemInfo.unadjustedStartOffset(anchorType.value!!))).roundToInt()

                // Go Up
                // nextItemBottomNoPadding uses the coordinate system of the underlying LazyList. It
                // keeps track of the top of the next potential list item that is a candidate to be
                // drawn in the viewport as we walk up the list items from the center. Going up
                // involved making offset smaller/negative as the coordinate system of the LazyList
                // starts at the top of the viewport. Note that the start of the lazy list
                // coordinates starts at '- start content padding in pixels' and goes beyond the
                // last visible list items to include the end content padding in pixels.

                // centralItem.offset is a startOffset in the coordinate system of the
                // underlying lazy list.
                var nextItemBottomNoPadding =
                    centralItemAdjustedUnderlyingOffset - gapBetweenItemsPx.value!!

                (centralItemArrayIndex - 1 downTo 0).forEach { ix ->
                    if (nextItemBottomNoPadding >= verticalAdjustment) {
                        val currentItem =
                            lazyListState.layoutInfo.visibleItemsInfo[ix]
                        if (!discardAutoCenteringListItem(currentItem)) {
                            val itemInfo = calculateItemInfo(
                                nextItemBottomNoPadding - currentItem.size,
                                currentItem,
                                verticalAdjustment,
                                viewportHeightPx,
                                scalingParams.value!!,
                                beforeContentPaddingPx.value!!,
                                anchorType.value!!,
                                autoCentering.value,
                                visible
                            )
                            visibleItemsInfo.add(0, itemInfo)
                            nextItemBottomNoPadding =
                                nextItemBottomNoPadding - itemInfo.size - gapBetweenItemsPx.value!!
                        }
                    } else {
                        return@forEach
                    }
                }

                // Go Down
                // nextItemTopNoPadding uses the coordinate system of the underlying LazyList. It
                // keeps track of the top of the next potential list item that is a candidate to be
                // drawn in the viewport as we walk down the list items from the center.
                var nextItemTopNoPadding =
                    centralItemAdjustedUnderlyingOffset + centerItemInfo.size +
                        gapBetweenItemsPx.value!!

                (((centralItemArrayIndex + 1) until
                    originalVisibleItemsInfo.size)).forEach { ix ->
                    if ((nextItemTopNoPadding - viewportHeightPx) <= verticalAdjustment) {
                        val currentItem =
                            lazyListState.layoutInfo.visibleItemsInfo[ix]
                        if (!discardAutoCenteringListItem(currentItem)) {
                            val itemInfo = calculateItemInfo(
                                nextItemTopNoPadding,
                                currentItem,
                                verticalAdjustment,
                                viewportHeightPx,
                                scalingParams.value!!,
                                beforeContentPaddingPx.value!!,
                                anchorType.value!!,
                                autoCentering.value,
                                visible
                            )

                            visibleItemsInfo.add(itemInfo)
                            nextItemTopNoPadding += itemInfo.size + gapBetweenItemsPx.value!!
                        }
                    } else {
                        return@forEach
                    }
                }
            }
            val totalItemsCount =
                if (autoCentering.value != null) {
                    (lazyListState.layoutInfo.totalItemsCount - 2).coerceAtLeast(0)
                } else {
                    lazyListState.layoutInfo.totalItemsCount
                }

            // Decide if we are ready for the 2nd stage of initialization
            // 1. We are not yet initialized and

            val readyForInitialScroll =
                if (! initialized.value) {
                    // 1. autoCentering is off or
                    // 2. The list has no items or
                    // 3. the before content autoCentering Spacer has been sized.
                    // NOTE: It is possible, if the first real item in the list is large, that the size
                    // of the Spacer is 0.
                    autoCentering.value == null || (
                        lazyListState.layoutInfo.visibleItemsInfo.size >= 2 && (
                            // or Empty list (other than the 2 spacers)
                            lazyListState.layoutInfo.visibleItemsInfo.size == 2 ||
                                // or first item is non-zero size
                                lazyListState.layoutInfo.visibleItemsInfo.first().size > 0 ||
                                // or first item is supposed to be zero size
                                calculateTopAutoCenteringPaddingFromLazyListItemInfo(
                                    lazyListState.layoutInfo.visibleItemsInfo,
                                    lazyListState.layoutInfo.totalItemsCount
                                ) == 0
                            )
                        )
                } else {
                    // We are already initialized and have an incomplete scroll to finish
                    incompleteScrollItem.value != null
                }

            DefaultScalingLazyListLayoutInfo(
                visibleItemsInfo = visibleItemsInfo,
                totalItemsCount = totalItemsCount,
                viewportStartOffset = lazyListState.layoutInfo.viewportStartOffset +
                    extraPaddingPx.value!!,
                viewportEndOffset = lazyListState.layoutInfo.viewportEndOffset -
                    extraPaddingPx.value!!,
                centerItemIndex = if (initialized.value) newCenterItemIndex else 0,
                centerItemScrollOffset = if (initialized.value) newCenterItemScrollOffset else 0,
                reverseLayout = reverseLayout.value!!,
                orientation = lazyListState.layoutInfo.orientation,
                viewportSize = IntSize(
                    width = lazyListState.layoutInfo.viewportSize.width,
                    height = lazyListState.layoutInfo.viewportSize.height -
                        extraPaddingPx.value!! * 2
                ),
                beforeContentPadding = beforeContentPaddingPx.value!!,
                afterContentPadding = afterContentPaddingPx.value!!,
                beforeAutoCenteringPadding = calculateTopAutoCenteringPaddingPx(visibleItemsInfo,
                    totalItemsCount),
                afterAutoCenteringPadding = calculateBottomAutoCenteringPaddingPx(visibleItemsInfo,
                    totalItemsCount),
                readyForInitialScroll = readyForInitialScroll,
                initialized = initialized.value
            )
        }
    }

    private fun findItemNearestCenter(
        viewportHeightPx: Int,
        verticalAdjustment: Int
    ): Int? {
        val centerLine = viewportHeightPx / 2
        var result: Int? = null
        // Find the item in the middle of the viewport
        for (i in lazyListState.layoutInfo.visibleItemsInfo.indices) {
            val item = lazyListState.layoutInfo.visibleItemsInfo[i]
            if (! discardAutoCenteringListItem(item)) {
                val rawItemStart = item.offset - verticalAdjustment
                val rawItemEnd = rawItemStart + item.size
                result = i
                if (rawItemEnd > centerLine) {
                    break
                }
            }
        }
        return result
    }

    companion object {
        /**
         * The default [Saver] implementation for [ScalingLazyListState].
         */
        val Saver = listSaver<ScalingLazyListState, Int>(
            save = {
                listOf(
                    it.centerItemIndex,
                    it.centerItemScrollOffset,
                )
            },
            restore = {
                val scalingLazyColumnState = ScalingLazyListState(it[0], it[1])
                scalingLazyColumnState
            }
        )
    }

    override val isScrollInProgress: Boolean
        get() {
            return lazyListState.isScrollInProgress
        }

    override fun dispatchRawDelta(delta: Float): Float {
        return lazyListState.dispatchRawDelta(delta)
    }

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ) {
        lazyListState.scroll(scrollPriority = scrollPriority, block = block)
    }

    /**
     * Instantly brings the item at [index] to the center of the viewport and positions it based on
     * the [anchorType] and applies the [scrollOffset] pixels.
     *
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll. Note that
     * positive offset refers to forward scroll, so in a top-to-bottom list, positive offset will
     * scroll the item further upward (taking it partly offscreen).
     */
    public suspend fun scrollToItem(
        /*@IntRange(from = 0)*/
        index: Int,
        /*@IntRange(from = 0)*/
        scrollOffset: Int = 0
    ) {
        return scrollToItem(false, index, scrollOffset)
    }

    /**
     * Brings the item at [index] to the center of the viewport and positions it based on
     * the [anchorType] and applies the [scrollOffset] pixels.
     *
     * @param animated whether to animate the scroll
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll. Note that
     * positive offset refers to forward scroll, so in a top-to-bottom list, positive offset will
     * scroll the item further upward (taking it partly offscreen).
     */
    internal suspend fun scrollToItem(
        animated: Boolean,
        /*@IntRange(from = 0)*/
        index: Int,
        /*@IntRange(from = 0)*/
        scrollOffset: Int,
    ) {
        if (!initialized.value) {
            // We can't scroll yet, save to do it when we can (on the first composition).
            initialCenterItemIndex = index
            initialCenterItemScrollOffset = scrollOffset
            return
        }

        // Find the underlying LazyList index taking into account the Spacer added before
        // the first ScalingLazyColumn list item when autoCentering
        val targetIndex = index.coerceAtMost(layoutInfo.totalItemsCount)
        val lazyListStateIndex = targetIndex + if (autoCentering.value != null) 1 else 0

        val offsetToCenterOfViewport =
            beforeContentPaddingPx.value!! - (viewportHeightPx.value!! / 2)
        if (anchorType.value == ScalingLazyListAnchorType.ItemStart) {
            val offset = offsetToCenterOfViewport + scrollOffset
            return lazyListState.scrollToItem(animated, lazyListStateIndex, offset)
        } else {
            var item = lazyListState.layoutInfo.findItemInfoWithIndex(lazyListStateIndex)
            if (item == null) {
                val estimatedUnadjustedHeight = layoutInfo.averageUnadjustedItemSize()
                val estimatedOffset =
                    offsetToCenterOfViewport + (estimatedUnadjustedHeight / 2) + scrollOffset

                // Scroll the item into the middle of the viewport so that we know it is visible
                lazyListState.scrollToItem(animated, lazyListStateIndex, estimatedOffset)
                // Now we know that the item is visible find it and fine tune our position
                item = lazyListState.layoutInfo.findItemInfoWithIndex(lazyListStateIndex)
            }
            if (item != null) {
                // Decide if the item is in the right place
                if (centerItemIndex != index || centerItemScrollOffset != scrollOffset) {
                    val offset = offsetToCenterOfViewport + (item.size / 2) + scrollOffset
                    return lazyListState.scrollToItem(animated, lazyListStateIndex, offset)
                }
            } else {
                // The scroll has failed - this should only happen if the list is not currently
                // visible
                incompleteScrollItem.value = targetIndex
                incompleteScrollOffset.value = scrollOffset
                incompleteScrollAnimated.value = animated
            }
        }
        return
    }

    internal suspend fun scrollToInitialItem() {
        // First time initialization
        if (!initialized.value) {
            initialized.value = true
            scrollToItem(initialCenterItemIndex, initialCenterItemScrollOffset)
        }
        // Check whether we are becoming visible after an incomplete scrollTo/animatedScrollTo
        if (incompleteScrollItem.value != null) {
            val animated = incompleteScrollAnimated.value
            val targetIndex = incompleteScrollItem.value!!
            val targetOffset = incompleteScrollOffset.value!!
            // Reset the incomplete scroll indicator
            incompleteScrollItem.value = null
            scrollToItem(animated, targetIndex, targetOffset)
        }
        return
    }

    /**
     * Animate (smooth scroll) the given item at [index] to the center of the viewport and position
     * it based on the [anchorType] and applies the [scrollOffset] pixels.
     *
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll (same as
     * [scrollToItem]) - note that positive offset refers to forward scroll, so in a
     * top-to-bottom list, positive offset will scroll the item further upward (taking it partly
     * offscreen)
     */
    public suspend fun animateScrollToItem(
        /*@IntRange(from = 0)*/
        index: Int,
        /*@IntRange(from = 0)*/
        scrollOffset: Int = 0
    ) {
        return scrollToItem(true, index, scrollOffset)
    }

    private fun discardAutoCenteringListItem(item: LazyListItemInfo): Boolean =
        autoCentering.value != null &&
            (item.index == 0 || item.index == lazyListState.layoutInfo.totalItemsCount - 1)

    /**
     * Calculate the amount of top padding needed (if any) to make sure that the
     * [AutoCenteringParams.itemIndex] item can be placed in the center of the viewport at
     * [AutoCenteringParams.itemOffset]
     */
    private fun calculateTopAutoCenteringPaddingPx(
        visibleItems: List<ScalingLazyListItemInfo>,
        totalItemCount: Int
    ): Int {
        if (autoCentering.value == null || visibleItems.isEmpty() ||
            visibleItems.first().index != 0) return 0

        // Work out the index we want to find - if there are less items in the list than would be
        // needed to make initialItemIndex be visible then use the last visible item
        val itemIndexToFind = autoCentering.value!!.itemIndex.coerceAtMost(totalItemCount - 1)

        // Find the initialCenterItem, if it is null that means it is not in view - therefore
        // we have more than enough content before it to make sure it can be scrolled to the center
        // of the viewport
        val initialCenterItemSize =
            visibleItems.find { it.index == itemIndexToFind }?.unadjustedSize ?: return 0

        // Determine how much space we actually need
        var spaceNeeded = spaceNeeded(initialCenterItemSize)

        if (spaceNeeded > 0f) {
            // Now see how much content we already have
            visibleItems.map {
                if (it.index < itemIndexToFind) {
                    // Reduce the space needed
                    spaceNeeded = spaceNeeded - gapBetweenItemsPx.value!! - it.unadjustedSize
                }
            }
        }
        return (spaceNeeded.roundToInt() + gapBetweenItemsPx.value!!).coerceAtLeast(0)
    }

    /**
     * Calculate the amount of top padding needed (if any) to make sure that the
     * [AutoCenteringParams.itemIndex] item can be placed in the center of the viewport at
     * [AutoCenteringParams.itemOffset]
     */
    private fun calculateTopAutoCenteringPaddingFromLazyListItemInfo(
        visibleItems: List<LazyListItemInfo>,
        totalItemCount: Int
    ): Int {
        // Check is list is empty or we are not at the start of the visible items
        if (autoCentering.value == null || visibleItems.isEmpty() ||
            visibleItems[0].index != 0) return 0

        // Work out the index we want to find - if there are less items in the list than would be
        // needed to make initialItemIndex be visible then use the last visible item. The -3 is to
        // allow for the spacers, i.e. an underlying list of size 3 has 2 spacers in index 0 and 2
        // and one real item in index 1.
        val itemIndexToFind = (autoCentering.value!!.itemIndex + 1).coerceAtMost(totalItemCount - 3)

        // Find the initialCenterItem, if it is null that means it is not in view - therefore
        // we have more than enough content before it to make sure it can be scrolled to the center
        // of the viewport
        val initialCenterItemSize =
            visibleItems.find { it.index == itemIndexToFind }?.size ?: return 0

        // Determine how much space we actually need
        var spaceNeeded = spaceNeeded(initialCenterItemSize)

        if (spaceNeeded > 0f) {
            // Now see how much content we already have
            visibleItems.map {
                if (it.index != 0 && it.index < itemIndexToFind) {
                    // Reduce the space needed
                    spaceNeeded = spaceNeeded - gapBetweenItemsPx.value!! - it.size
                }
            }
        }
        return (spaceNeeded.roundToInt() + gapBetweenItemsPx.value!!).coerceAtLeast(0)
    }

    private fun spaceNeeded(unadjustedSize: Int) =
        if (anchorType.value == ScalingLazyListAnchorType.ItemStart) {
            viewportHeightPx.value!! / 2f
        } else {
            viewportHeightPx.value!! / 2f -
                unadjustedSize / 2f
        } - gapBetweenItemsPx.value!! - autoCentering.value!!.itemOffset

    private fun calculateBottomAutoCenteringPaddingPx(
        visibleItemsInfo: List<ScalingLazyListItemInfo>,
        totalItemsCount: Int
    ) = if (autoCentering.value != null && visibleItemsInfo.isNotEmpty() &&
        visibleItemsInfo.last().index == totalItemsCount - 1
    ) {
        if (anchorType.value == ScalingLazyListAnchorType.ItemStart) {
            viewportHeightPx.value!! / 2f - visibleItemsInfo.last().size
        } else {
            viewportHeightPx.value!! / 2f -
                visibleItemsInfo.last().unadjustedSize / 2f
        }.roundToInt()
    } else {
        0
    }
}

private fun LazyListLayoutInfo.findItemInfoWithIndex(index: Int): LazyListItemInfo? {
    return this.visibleItemsInfo.find { it.index == index }
}

private suspend fun LazyListState.scrollToItem(animated: Boolean, index: Int, offset: Int) {
    if (animated) animateScrollToItem(index, offset) else scrollToItem(index, offset)
}

private fun ScalingLazyListLayoutInfo.averageUnadjustedItemSize(): Int {
    var totalSize = 0
    visibleItemsInfo.forEach { totalSize += it.unadjustedSize }
    return if (visibleItemsInfo.isNotEmpty())
        (totalSize.toFloat() / visibleItemsInfo.size).roundToInt()
    else 0
}

private object EmptyScalingLazyListLayoutInfo : ScalingLazyListLayoutInfo {
    override val visibleItemsInfo = emptyList<ScalingLazyListItemInfo>()
    override val viewportStartOffset = 0
    override val viewportEndOffset = 0
    override val totalItemsCount = 0
    override val viewportSize = IntSize.Zero
    override val orientation = Orientation.Vertical
    override val reverseLayout = false
    override val beforeContentPadding = 0
    override val afterContentPadding = 0
    override val beforeAutoCenteringPadding = 0
    override val afterAutoCenteringPadding = 0
}
