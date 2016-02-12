package com.sss.magicwheel;

import android.app.Fragment;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sss.magicwheel.entity.WheelConfig;
import com.sss.magicwheel.entity.WheelDataItem;
import com.sss.magicwheel.manager.WheelComputationHelper;
import com.sss.magicwheel.manager.widget.WheelOfFortuneContainerFrameView;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kovalev
 * @since 01.02.2016.
 */
public final class FragmentWheelPage extends Fragment {

    private static final int DEFAULT_SECTOR_ANGLE_IN_DEGREE = 20;

    private boolean isWheelContainerInitialized;

    private final Handler handler = new Handler();

    private WheelOfFortuneContainerFrameView wheelOfFortuneContainerFrameView;

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {

        // TODO: 03.02.2016 simplify for now considering container has 0 height

        WheelComputationHelper.initialize(createWheelConfig(0));

        final View rootView = inflater.inflate(R.layout.fragment_wheel_page_layout, container, false);

        wheelOfFortuneContainerFrameView = (WheelOfFortuneContainerFrameView) rootView.findViewById(R.id.wheel_of_fortune_container_frame);
        wheelOfFortuneContainerFrameView.swapData(createSampleDataSet());

        /*rootView.findViewById(R.id.fragment_request_layout_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wheelOfFortuneContainerFrameView.requestLayout();
            }
        });*/

/*
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                if (!isWheelContainerInitialized) {
                    isWheelContainerInitialized = true;
                    final int fragmentContainerTopEdge = container.getTop();
                    WheelComputationHelper.initialize(createWheelConfig(fragmentContainerTopEdge));
                    initTopWheelContainer(topWheelContainerView);
                }
            }
        });
*/
        return rootView;
    }

    private List<WheelDataItem> createSampleDataSet() {
        List<WheelDataItem> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            items.add(new WheelDataItem("item.Num [" + i + "]"));
        }
        return items;
    }


    private WheelConfig createWheelConfig(int fragmentContainerTopEdge) {
        final int screenHeight = WheelComputationHelper.getScreenDimensions(getActivity()).getHeight();

        final int yWheelCenterPosition = (screenHeight - fragmentContainerTopEdge) / 2;
        final PointF circleCenter = new PointF(0, yWheelCenterPosition);

        // TODO: 03.12.2015 Not good hardcoded values
        final int outerRadius = (screenHeight - fragmentContainerTopEdge) / 2;
        final int innerRadius = outerRadius - 300;

        final double sectorAngleInRad = WheelComputationHelper.degreeToRadian(DEFAULT_SECTOR_ANGLE_IN_DEGREE);
        final WheelConfig.AngularRestrictions angularRestrictions = WheelConfig.AngularRestrictions
                .builder(sectorAngleInRad)
                .wheelEdgesAngularRestrictions(Math.PI / 2, -Math.PI / 2)
                .gapEdgesAngularRestrictions(Math.PI / 6, -Math.PI / 6)
                .build();

        return new WheelConfig(circleCenter, outerRadius, innerRadius, angularRestrictions);
    }

}
