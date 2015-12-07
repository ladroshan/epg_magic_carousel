package com.sss.magicwheel.manager;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.sss.magicwheel.entity.CoordinatesHolder;
import com.sss.magicwheel.entity.LinearClipData;

import java.util.List;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

/**
 * @author Alexey Kovalev
 * @since 04.12.2015.
 */
public final class WheelLayoutManager extends RecyclerView.LayoutManager {

    public static final String TAG = WheelLayoutManager.class.getCanonicalName();

    private final Context context;
    private final CircleConfig circleConfig;
    private final ComputationHelper computationHelper;

    private final LayoutState mLayoutState;

    public WheelLayoutManager(Context context, CircleConfig circleConfig) {
        this.context = context;
        this.circleConfig = circleConfig;
        this.mLayoutState = new LayoutState();
        this.computationHelper = new ComputationHelper(circleConfig);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {

        removeAndRecycleAllViews(recycler);

        final double sectorAngleInRad = circleConfig.getAngularRestrictions().getSectorAngleInRad();
        addViewForPosition(recycler, 0, -2 * sectorAngleInRad);
        addViewForPosition(recycler, 1, -sectorAngleInRad);
        addViewForPosition(recycler, 2, 0);
        addViewForPosition(recycler, 3, sectorAngleInRad);
        addViewForPosition(recycler, 4, 2 * sectorAngleInRad);
    }

    private void addViewForPosition(RecyclerView.Recycler recycler, int position, double angleInRad) {
        final WheelBigWrapperView bigWrapperView = (WheelBigWrapperView) recycler.getViewForPosition(position);

        measureBigWrapperView(bigWrapperView);

        Rect wrViewCoordsInCircleSystem = getWrapperViewCoordsInCircleSystem(bigWrapperView.getMeasuredWidth());
//        Log.e(TAG, "Before transformation [" + wrViewCoordsInCircleSystem.toShortString() + "]");

        Rect wrTransformedCoords = WheelUtils.fromCircleCoordsSystemToRecyclerViewCoordsSystem(
                circleConfig.getCircleCenterRelToRecyclerView(),
                wrViewCoordsInCircleSystem
        );

//        Log.e(TAG, "After transformation " + wrTransformedCoords.toShortString());
//        Log.e(TAG, "Sector wrapper width [" + computationHelper.getSectorWrapperViewWidth() + "]");

        bigWrapperView.layout(wrTransformedCoords.left, wrTransformedCoords.top, wrTransformedCoords.right, wrTransformedCoords.bottom);

        rotateBigWraperViewToAngle(bigWrapperView, angleInRad);

        bigWrapperView.setSectorWrapperViewSize(
                computationHelper.getSectorWrapperViewWidth(),
                computationHelper.getSectorWrapperViewHeight()
        );

        bigWrapperView.setSectorClipArea(computationHelper.createSectorClipArea());

        LayoutParams lp = (LayoutParams) bigWrapperView.getLayoutParams();
        lp.rotationAngleInRad = -angleInRad;

        addView(bigWrapperView);
    }

    private Rect getWrapperViewCoordsInCircleSystem(int wrapperViewWidth) {
        final int topEdge = computationHelper.getSectorWrapperViewHeight() / 2;
        return new Rect(0, topEdge, wrapperViewWidth, -topEdge);
    }

    private void rotateBigWraperViewToAngle(View bigWrapperView, double angleToRotateInRad) {
        bigWrapperView.setPivotX(0);
        bigWrapperView.setPivotY(bigWrapperView.getMeasuredHeight() / 2);
        bigWrapperView.setRotation(WheelUtils.radToDegree(angleToRotateInRad));
    }

    private void measureBigWrapperView(View bigWrapperView) {
        final int viewWidth = circleConfig.getOuterRadius();
        // big wrapper view has the same height as the sector wrapper view
        final int viewHeight = computationHelper.getSectorWrapperViewHeight();

        final int childWidthSpec = View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY);
        final int childHeightSpec = View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY);
        bigWrapperView.measure(childWidthSpec, childHeightSpec);
    }


    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        double angleToScrollInRad = Math.asin(Math.abs(dy) / circleConfig.getOuterRadius());
        return rotateCircleByAngle(angleToScrollInRad, CircleRotationDirection.of(dy), recycler, state);
    }

    private int rotateCircleByAngle(double angleToScrollInRad,
                                    CircleRotationDirection circleRotationDirection,
                                    RecyclerView.Recycler recycler, RecyclerView.State state) {

        if (getChildCount() == 0 || angleToScrollInRad == 0) {
            return 0;
        }
        mLayoutState.mRecycle = true;
        updateLayoutState(angleToScrollInRad, circleRotationDirection, true);

//        final int freeScroll = mLayoutState.mScrollingOffset;

        final int consumedAngle = /*freeScroll +*/ fillCircleLayout(recycler, mLayoutState, state);
        if (consumedAngle < 0) {
            return 0;
        }

        final double actualRotationAngle = angleToScrollInRad > consumedAngle ?
                circleRotationDirection.direction * consumedAngle : circleRotationDirection.direction * angleToScrollInRad;

        doChildrenRotationByAngle(angleToScrollInRad, circleRotationDirection);

        // TODO: 07.12.2015 most probably this computation is not correct
        return (int) (circleConfig.getOuterRadius() * Math.sin(actualRotationAngle));
    }

    private void doChildrenRotationByAngle(double angleToScrollInRad, CircleRotationDirection circleRotationDirection) {
        // TODO: 07.12.2015 Big wrappers rotation logic here
    }


    private void updateLayoutState(double angleToScrollInRad, CircleRotationDirection circleRotationDirection,
                                   /*int layoutDirection, int requiredSpace,*/ boolean canUseExistingSpace) {

        mLayoutState.mRotationDirection = circleRotationDirection;
        int fastScrollSpace;
        if (circleRotationDirection == CircleRotationDirection.Anticlockwise) {
            // get the first child in the direction we are going
            final View child = getChildClosestToEnd();
            final LayoutParams childLp = (LayoutParams) child.getLayoutParams();

            mLayoutState.mCurrentPosition = getPosition(child) + 1;
            // here we need to calculate angular position of the sector's bottom edge because in LP
            // we remember only top edge angular pos
            mLayoutState.mAngleToStartLayout = childLp.rotationAngleInRad - circleConfig.getAngularRestrictions().getSectorAngleInRad();

            // calculate how much we can scroll without adding new children (independent of layout)
//            fastScrollSpace = mOrientationHelper.getDecoratedEnd(child)
//                    - mOrientationHelper.getEndAfterPadding();

        } else {

            // TODO: 07.12.2015 add implementation
            throw new UnsupportedOperationException("Add implementation");

            /*final View child = getChildClosestToStart();
            mLayoutState.mExtra += mOrientationHelper.getStartAfterPadding();
            mLayoutState.mItemDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_TAIL
                    : LayoutState.ITEM_DIRECTION_HEAD;
            mLayoutState.mCurrentPosition = getPosition(child) + mLayoutState.mItemDirection;
            mLayoutState.mOffset = mOrientationHelper.getDecoratedStart(child);
            fastScrollSpace = -mOrientationHelper.getDecoratedStart(child)
                    + mOrientationHelper.getStartAfterPadding();*/
        }
        mLayoutState.mRequestedScrollAngle = angleToScrollInRad;
//        if (canUseExistingSpace) {
//            mLayoutState.mAvailable -= fastScrollSpace;
//        }
//        mLayoutState.mScrollingOffset = fastScrollSpace;
    }


    private View getChildClosestToEnd() {
        return getChildAt(getChildCount() - 1);
    }


    /**
     * Fills the given layout, defined by configured beforehand layoutState.
     */
    private int fillCircleLayout(RecyclerView.Recycler recycler, LayoutState layoutState, RecyclerView.State state) {

        // max offset we should set is mFastScroll + available
//        final int start = layoutState.mAvailable;
//        if (layoutState.mScrollingOffset != LayoutState.SCOLLING_OFFSET_NaN) {
//            // TODO ugly bug fix. should not happen
//            if (layoutState.mAvailable < 0) {
//                layoutState.mScrollingOffset += layoutState.mAvailable;
//            }
//            recycleByLayoutState(recycler, layoutState);
//        }


        double angleToCompensate = layoutState.mRequestedScrollAngle;

        LayoutChunkResult layoutChunkResult = new LayoutChunkResult();
        while (angleToCompensate > 0 && layoutState.hasMore(state)) {
            layoutChunkResult.resetInternal();
            layoutCircleChunk(recycler, state, layoutState, layoutChunkResult);
            if (layoutChunkResult.mFinished) {
                break;
            }
            layoutState.mOffset += layoutChunkResult.mConsumedAngle * layoutState.mLayoutDirection;

            /**
             * Consume the available space if:
             * * layoutCircleChunk did not request to be ignored
             * * OR we are laying out scrap children
             * * OR we are not doing pre-layout
             */
            if (!layoutChunkResult.mIgnoreConsumed || mLayoutState.mScrapList != null
                    || !state.isPreLayout()) {
                layoutState.mAvailable -= layoutChunkResult.mConsumedAngle;
                // we keep a separate remaining space because mAvailable is important for recycling
                angleToCompensate -= layoutChunkResult.mConsumedAngle;
            }

            if (layoutState.mScrollingOffset != LayoutState.SCOLLING_OFFSET_NaN) {
                layoutState.mScrollingOffset += layoutChunkResult.mConsumedAngle;
                if (layoutState.mAvailable < 0) {
                    layoutState.mScrollingOffset += layoutState.mAvailable;
                }
                recycleByLayoutState(recycler, layoutState);
            }

        }

        return start - layoutState.mAvailable;
    }

    private void layoutCircleChunk(RecyclerView.Recycler recycler, RecyclerView.State state,
                           LayoutState layoutState, LayoutChunkResult result) {

        View view = layoutState.next(recycler);
        if (view == null) {
            // if we are laying out views in scrap, this may return null which means there is
            // no more items to layout.
            result.mFinished = true;
            return;
        }

        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
        if (layoutState.mScrapList == null) {
            if (layoutState.mRotationDirection == CircleRotationDirection.Anticlockwise) {
                addView(view);
            } else {
                addView(view, 0);
            }
        }

        layoutViewFromChunk(view);

        result.mConsumedAngle = circleConfig.getAngularRestrictions().getSectorAngleInRad();

        // Consume the available space if the view is not removed OR changed
        if (params.isItemRemoved() || params.isItemChanged()) {
            result.mIgnoreConsumed = true;
        }
    }



    private void layoutViewFromChunk(View viewToAdd) {

        WheelBigWrapperView bigWrapperView = (WheelBigWrapperView) viewToAdd;

        measureBigWrapperView(viewToAdd);

        Rect wrViewCoordsInCircleSystem = getWrapperViewCoordsInCircleSystem(viewToAdd.getMeasuredWidth());

        Rect wrTransformedCoords = WheelUtils.fromCircleCoordsSystemToRecyclerViewCoordsSystem(
                circleConfig.getCircleCenterRelToRecyclerView(),
                wrViewCoordsInCircleSystem
        );

        viewToAdd.layout(
                wrTransformedCoords.left,
                wrTransformedCoords.top,
                wrTransformedCoords.right,
                wrTransformedCoords.bottom
        );

        rotateBigWraperViewToAngle(viewToAdd, angleInRad);

        bigWrapperView.setSectorWrapperViewSize(
                computationHelper.getSectorWrapperViewWidth(),
                computationHelper.getSectorWrapperViewHeight()
        );

        bigWrapperView.setSectorClipArea(computationHelper.createSectorClipArea());

        LayoutParams lp = (LayoutParams) viewToAdd.getLayoutParams();
        lp.rotationAngleInRad = -angleInRad;

        addView(viewToAdd);

    }

    @Override
    public boolean canScrollHorizontally() {
        return false;
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return false;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        );
    }

    private enum CircleRotationDirection {

        Clockwise(-1), Anticlockwise(1);

        private final int direction;

        /**
         * When we move from circle's HEAD to TAIL (anticlockwise) - we increase
         * adapter position, and decrease it when scrolling clockwise.
         */
        private final int adapterPositionIncrementation;

        CircleRotationDirection(int directionSignum) {
            this.direction = directionSignum;
            this.adapterPositionIncrementation = directionSignum;
        }

        public static CircleRotationDirection of(int directionAsInt) {
            return directionAsInt < 0 ? Clockwise : Anticlockwise;
        }
    }

    /**
     * Helper class that keeps temporary state while {LayoutManager} is filling out the empty
     * space.
     */
    private static class LayoutState {

        final static int INVALID_LAYOUT = Integer.MIN_VALUE;

        final static int SCOLLING_OFFSET_NaN = Integer.MIN_VALUE;

        /**
         * We may not want to recycle children in some cases (e.g. layout)
         */
        boolean mRecycle = true;

        double mRequestedScrollAngle;
        CircleRotationDirection mRotationDirection;

        /**
         * Starting from this angle new children will be added to circle.
         */
        double mAngleToStartLayout;

        /**
         * Used when LayoutState is constructed in a scrolling state.
         * It should be set the amount of scrolling we can make without creating a new view.
         * Settings this is required for efficient view recycling.
         */
        int mRecycleSweepAngle;

        /**
         * Current position on the adapter to get the next item.
         */
        int mCurrentPosition;

        /**
         * When LLM needs to layout particular views, it sets this list in which case, LayoutState
         * will only return views from this list and return null if it cannot find an item.
         */
        List<RecyclerView.ViewHolder> mScrapList = null;



        /**
         * @return true if there are more items in the data adapter
         */
        boolean hasMore(RecyclerView.State state) {
            return mCurrentPosition >= 0 && mCurrentPosition < state.getItemCount();
        }

        /**
         * Gets the view for the next element that we should layout.
         * Also updates current item index to the next item, based on {@code mItemFetchDirection}
         *
         * @return The next element that we should layout.
         */
        View next(RecyclerView.Recycler recycler) {
            if (mScrapList != null) {
                return nextViewFromScrapList();
            }
            final View view = recycler.getViewForPosition(mCurrentPosition);
            mCurrentPosition += mRotationDirection.adapterPositionIncrementation;
            return view;
        }

        /**
         * Returns the next item from the scrap list.
         * <p>
         * Upon finding a valid VH, sets current item position to VH.itemPosition + mItemFetchDirection
         *
         * @return View if an item in the current position or direction exists if not null.
         */
        private View nextViewFromScrapList() {
            final int size = mScrapList.size();
            for (int i = 0; i < size; i++) {
                final View view = mScrapList.get(i).itemView;
                final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
                if (lp.isItemRemoved()) {
                    continue;
                }
                if (mCurrentPosition == lp.getViewLayoutPosition()) {
                    assignPositionFromScrapList(view);
                    return view;
                }
            }
            return null;
        }

        public void assignPositionFromScrapList() {
            assignPositionFromScrapList(null);
        }

        public void assignPositionFromScrapList(View ignore) {
            final View closest = nextViewInLimitedList(ignore);
            if (closest == null) {
                mCurrentPosition = NO_POSITION;
            } else {
                mCurrentPosition = ((RecyclerView.LayoutParams) closest.getLayoutParams())
                        .getViewLayoutPosition();
            }
        }

        public View nextViewInLimitedList(View ignore) {
            int size = mScrapList.size();
            View closest = null;
            int closestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                View view = mScrapList.get(i).itemView;
                final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
                if (view == ignore || lp.isItemRemoved()) {
                    continue;
                }
                final int distance = (lp.getViewLayoutPosition() - mCurrentPosition) * mRotationDirection.adapterPositionIncrementation;
                if (distance < 0) {
                    continue; // item is not in current direction
                }
                if (distance < closestDistance) {
                    closest = view;
                    closestDistance = distance;
                    if (distance == 0) {
                        break;
                    }
                }
            }
            return closest;
        }

    }

    private static class LayoutChunkResult {
        public double mConsumedAngle;
        public boolean mFinished;
        public boolean mIgnoreConsumed;

        void resetInternal() {
            mConsumedAngle = 0;
            mFinished = false;
            mIgnoreConsumed = false;
        }
    }

    private static final class ComputationHelper {

        private static final int NOT_DEFINED_VALUE = Integer.MIN_VALUE;

        private final CircleConfig circleConfig;

        private int sectorWrapperViewWidth = NOT_DEFINED_VALUE;
        private int sectorWrapperViewHeight = NOT_DEFINED_VALUE;

        public ComputationHelper(CircleConfig circleConfig) {
            this.circleConfig = circleConfig;
        }

        /**
         * Width of the view which wraps the sector.
         */
        public int getSectorWrapperViewWidth() {
            if (sectorWrapperViewWidth == NOT_DEFINED_VALUE) {
                sectorWrapperViewWidth = computeViewWidth();
            }
            return sectorWrapperViewWidth;
        }

        private int computeViewWidth() {
            final double delta = circleConfig.getInnerRadius() * Math.cos(circleConfig.getAngularRestrictions().getSectorAngleInRad() / 2);
            return (int) (circleConfig.getOuterRadius() - delta);
        }

        /**
         * Height of the view which wraps the sector.
         */
        public int getSectorWrapperViewHeight() {
            if (sectorWrapperViewHeight == NOT_DEFINED_VALUE) {
                sectorWrapperViewHeight = computeViewHeight();
            }
            return sectorWrapperViewHeight;
        }

        private int computeViewHeight() {
            final double halfHeight = circleConfig.getOuterRadius() * Math.sin(circleConfig.getAngularRestrictions().getSectorAngleInRad() / 2);
            return (int) (2 * halfHeight);
        }


        public LinearClipData createSectorClipArea() {
            final int viewWidth = getSectorWrapperViewWidth();
            final int viewHalfHeight = getSectorWrapperViewHeight() / 2;

            final double leftBaseDelta = circleConfig.getInnerRadius() * Math.sin(circleConfig.getAngularRestrictions().getSectorAngleInRad() / 2);
            final double rightBaseDelta = circleConfig.getOuterRadius() * Math.sin(circleConfig.getAngularRestrictions().getSectorAngleInRad() / 2);

            final CoordinatesHolder first = CoordinatesHolder.ofRect(0, viewHalfHeight + leftBaseDelta);
            final CoordinatesHolder third = CoordinatesHolder.ofRect(0, viewHalfHeight - leftBaseDelta);

            final CoordinatesHolder second = CoordinatesHolder.ofRect(viewWidth, viewHalfHeight + rightBaseDelta);
            final CoordinatesHolder forth = CoordinatesHolder.ofRect(viewWidth, viewHalfHeight - rightBaseDelta);

            return new LinearClipData(first, second, third, forth);
        }
    }

    public static final class LayoutParams extends RecyclerView.LayoutParams {

        /**
         * Rotation angle defined by top edge of the sector.
         */
        double rotationAngleInRad;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(RecyclerView.LayoutParams source) {
            super(source);
        }
    }
}