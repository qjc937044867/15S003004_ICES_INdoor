package com.qjc.IndoorNavigation.ImageMap;

import android.annotation.SuppressLint;
import android.graphics.PointF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

@SuppressLint("ViewConstructor")
public class Bubble extends FrameLayout{

   // static final boolean IS_API_11_LATER = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;

    public final View view;
    public final PointF position = new PointF();

    private RenderDelegate renderDelegate;

    public Bubble(View view){
		super(view.getContext());
        this.view = view;
        final int wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(wrapContent,wrapContent);
        this.view.setLayoutParams(params);
        this.view.setClickable(true);
		addView(view);
    }

	/**
	 * Bubble界面渲染代理。用以处理Bubble界面的数据填充。
	 */
	public interface RenderDelegate {
        void onDisplay(Shape shape, View bubbleView);
    }

    /**
     * 为Bubble界面设置一个渲染代理接口
     * @param renderDelegate 渲染代理接口
     */
    public void setRenderDelegate (RenderDelegate renderDelegate) {
        this.renderDelegate = renderDelegate;
    }

    /**
     * Show the bubble view controller on the shape.
     * @param shape the shape to show on
     */
    public void showAtShape(Shape shape){
        if(view == null) return;
        shape.createBubbleRelation(this);
        setBubbleViewAtPosition(shape.getCenterPoint());
        if (renderDelegate != null){
            renderDelegate.onDisplay(shape, view);
        }
        view.setVisibility(View.VISIBLE);
    }

    private void setBubbleViewAtPosition(PointF center){
        float posX = center.x - view.getWidth()/2;
        float posY = center.y - view.getHeight();
		setBubbleViewAtPosition(posX, posY);
    }

    private void setBubbleViewAtPosition(float x, float y){

		// BUG : HTC SDK 2.3.3 界面会被不停的重绘,这个重绘请求是View.onDraw()方法发起的。
		if(position.equals(x,y)) return;

        position.set(x,y);

		//if(IS_API_11_LATER){
         //   view.setX(x);
          //  view.setY(y);
       // }else{
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
			int left = (int)x;
			int top = (int)y;
			// HTC SDK 2.3.3 Required
			params.gravity = Gravity.CENTER_VERTICAL | Gravity.TOP;
			params.leftMargin = left;
			params.topMargin = top;
			view.setLayoutParams(params);
        //}
    }

}
