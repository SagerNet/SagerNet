package com.takisoft.preferencex.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;

import com.takisoft.preferencex.animation.SimpleMenuAnimation;
import com.takisoft.preferencex.drawable.FixedBoundsDrawable;
import com.takisoft.preferencex.simplemenu.R;

import java.util.Arrays;
import java.util.Comparator;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Extension of {@link PopupWindow} that implements
 * <a href="https://material.io/guidelines/components/menus.html#menus-simple-menus">Simple Menus</a>
 * in Material Design.
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class SimpleMenuPopupWindow extends PopupWindow {

    public static final int POPUP_MENU = 0;
    public static final int DIALOG = 1;

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public interface OnItemClickListener {
        void onClick(int i);
    }

    protected final int[] elevation = new int[2];
    protected final int[][] margin = new int[2][2];
    protected final int[][] listPadding = new int[2][2];
    protected final int itemHeight;
    protected final int dialogMaxWidth;
    protected final int unit;
    protected final int maxUnits;

    private int mMode = POPUP_MENU;

    private boolean mRequestMeasure = true;

    private RecyclerView mList;
    private SimpleMenuListAdapter mAdapter;

    private OnItemClickListener mOnItemClickListener;
    private CharSequence[] mEntries;
    private int mSelectedIndex;

    private int mMeasuredWidth;

    public SimpleMenuPopupWindow(Context context) {
        this(context, null);
    }

    public SimpleMenuPopupWindow(Context context, AttributeSet attrs) {
        this(context, attrs, R.styleable.SimpleMenuPreference_pref_popupStyle);
    }

    public SimpleMenuPopupWindow(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Preference_SimpleMenuPreference_Popup);
    }

    @SuppressLint("InflateParams")
    public SimpleMenuPopupWindow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setFocusable(true);
        setOutsideTouchable(false);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SimpleMenuPopup, defStyleAttr, defStyleRes);

        elevation[POPUP_MENU] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_listElevation, 4f);
        elevation[DIALOG] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_dialogElevation, 48f);
        margin[POPUP_MENU][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_listMarginHorizontal, 0);
        margin[POPUP_MENU][VERTICAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_listMarginVertical, 0);
        margin[DIALOG][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_dialogMarginHorizontal, 0);
        margin[DIALOG][VERTICAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_dialogMarginVertical, 0);
        listPadding[POPUP_MENU][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_listItemPadding, 0);
        listPadding[DIALOG][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_dialogItemPadding, 0);
        dialogMaxWidth  = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_dialogMaxWidth, 0);
        unit = (int) a.getDimension(R.styleable.SimpleMenuPopup_pref_unit, 0);
        maxUnits = a.getInteger(R.styleable.SimpleMenuPopup_pref_maxUnits, 0);

        mList = (RecyclerView) LayoutInflater.from(context).inflate(R.layout.simple_menu_list, null);
        mList.setFocusable(true);
        mList.setLayoutManager(new LinearLayoutManager(context));
        mList.setItemAnimator(null);
        setContentView(mList);

        mAdapter = new SimpleMenuListAdapter(this);
        mList.setAdapter(mAdapter);

        a.recycle();

        // TODO do not hardcode
        itemHeight = Math.round(context.getResources().getDisplayMetrics().density * 48);
        listPadding[POPUP_MENU][VERTICAL] = listPadding[DIALOG][VERTICAL] = Math.round(context.getResources().getDisplayMetrics().density * 8);
    }

    public OnItemClickListener getOnItemClickListener() {
        return mOnItemClickListener;
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    protected int getMode() {
        return mMode;
    }

    private void setMode(int mode) {
        mMode = mode;
    }

    protected CharSequence[] getEntries() {
        return mEntries;
    }

    public void setEntries(CharSequence[] entries) {
        mEntries = entries;
    }

    protected int getSelectedIndex() {
        return mSelectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        mSelectedIndex = selectedIndex;
    }

    @Override
    public RecyclerView getContentView() {
        return (RecyclerView) super.getContentView();
    }

    @Override
    public FixedBoundsDrawable getBackground() {
        Drawable background = super.getBackground();
        if (background != null
                && !(background instanceof FixedBoundsDrawable)) {
            setBackgroundDrawable(background);
        }
        return (FixedBoundsDrawable) super.getBackground();
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (background == null) {
            throw new IllegalStateException("SimpleMenuPopupWindow must have a background");
        }

        if (!(background instanceof FixedBoundsDrawable)) {
            background = new FixedBoundsDrawable(background);
        }
        super.setBackgroundDrawable(background);
    }

    /**
     * Show the PopupWindow
     *
     * @param anchor View that will be used to calc the position of windows
     * @param container View that will be used to calc the position of windows
     * @param extraMargin extra margin start
     */
    public void show(View anchor, View container, int extraMargin) {
        int maxMaxWidth = container.getWidth() - margin[POPUP_MENU][HORIZONTAL] * 2;
        int measuredWidth = measureWidth(maxMaxWidth, mEntries);
        if (measuredWidth == -1) {
            setMode(DIALOG);
        } else if (measuredWidth != 0) {
            setMode(POPUP_MENU);

            mMeasuredWidth = measuredWidth;
        }

        mAdapter.notifyDataSetChanged();

        if (mMode == POPUP_MENU) {
            showPopupMenu(anchor, container, mMeasuredWidth, extraMargin);
        } else {
            showDialog(anchor, container);
        }
    }

    /**
     * Show popup window in dialog mode
     *
     * @param parent a parent view to get the {@link android.view.View#getWindowToken()} token from
     * @param container Container view that holds preference list, also used to calc width
     */
    private void showDialog(View parent, View container) {
        final int index = Math.max(0, mSelectedIndex);
        final int count = mEntries.length;

        getContentView().setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        getContentView().scrollToPosition(index);

        int width = Math.min(dialogMaxWidth, container.getWidth() - margin[DIALOG][HORIZONTAL] * 2);
        setWidth(width);
        setHeight(WRAP_CONTENT);
        setAnimationStyle(R.style.Animation_SimpleMenuCenter);
        setElevation(elevation[DIALOG]);

        super.showAtLocation(parent, Gravity.CENTER_VERTICAL, 0, 0);

        getContentView().post(new Runnable() {
            @Override
            public void run() {
                int width = getContentView().getWidth();
                int height = getContentView().getHeight();
                Rect start = new Rect(width / 2, height / 2, width / 2, height / 2);

                SimpleMenuAnimation.startEnterAnimation(getContentView(), getBackground(),
                        width, height, width / 2, height / 2, start, itemHeight, elevation[DIALOG] / 4, index);
            }
        });

        getContentView().post(new Runnable() {
            @Override
            public void run() {
                // disable over scroll when no scroll
                LinearLayoutManager lm = (LinearLayoutManager) getContentView().getLayoutManager();
                if (lm.findFirstCompletelyVisibleItemPosition() == 0
                    && lm.findLastCompletelyVisibleItemPosition() == count - 1) {
                    getContentView().setOverScrollMode(View.OVER_SCROLL_NEVER);
                }
            }
        });
    }

    /**
     * Show popup window in popup mode
     *
     * @param anchor View that will be used to calc the position of the window
     * @param container Container view that holds preference list, also used to calc width
     * @param width Measured width of this window
     */
    private void showPopupMenu(View anchor, View container, int width, int extraMargin) {
        final boolean rtl = container.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        final int index = Math.max(0, mSelectedIndex);
        final int count = mEntries.length;

        final int anchorTop = anchor.getTop();
        final int anchorHeight = anchor.getHeight();
        final int measuredHeight = itemHeight * count + listPadding[POPUP_MENU][VERTICAL] * 2;

        int[] location = new int[2];
        container.getLocationInWindow(location);

        final int containerTopInWindow = location[1];
        final int containerHeight = container.getHeight();

        int y;

        int height = measuredHeight;
        int elevation = this.elevation[POPUP_MENU];
        int centerX = rtl
                ? location[0] + extraMargin - width + listPadding[POPUP_MENU][HORIZONTAL]
                : location[0] + extraMargin + listPadding[POPUP_MENU][HORIZONTAL];
        int centerY;
        int animItemHeight = itemHeight + listPadding[POPUP_MENU][VERTICAL] * 2;
        int animIndex = index;
        Rect animStartRect;

        if (height > containerHeight) {
            // too high, use scroll
            y = containerTopInWindow + margin[POPUP_MENU][VERTICAL];

            // scroll to select item
            final int scroll = itemHeight * index
                    - anchorTop + listPadding[POPUP_MENU][VERTICAL] + margin[POPUP_MENU][VERTICAL]
                    - anchorHeight / 2 + itemHeight / 2;

            getContentView().post(new Runnable() {
                @Override
                public void run() {
                    getContentView().scrollBy(0, -measuredHeight); // to top
                    getContentView().scrollBy(0, scroll);
                }
            });
            getContentView().setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

            height = containerHeight - margin[POPUP_MENU][VERTICAL] * 2;

            animIndex = index;

            centerY = itemHeight * index;
        } else {
            // calc align to selected
            y = containerTopInWindow + anchorTop + anchorHeight / 2 - itemHeight / 2
                    - listPadding[POPUP_MENU][VERTICAL] - index * itemHeight;

            // make sure window is in parent view
            int maxY = containerTopInWindow + containerHeight
                    - measuredHeight - margin[POPUP_MENU][VERTICAL];
            y = Math.min(y, maxY);

            int minY = containerTopInWindow + margin[POPUP_MENU][VERTICAL];
            y = Math.max(y, minY);

            getContentView().setOverScrollMode(View.OVER_SCROLL_NEVER);

            // center of selected item
            centerY = (int) (listPadding[POPUP_MENU][VERTICAL] + index * itemHeight + itemHeight * 0.5);
        }

        setWidth(width);
        setHeight(height);
        setElevation(elevation);
        setAnimationStyle(R.style.Animation_SimpleMenuCenter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setEnterTransition(null);
            setExitTransition(null);
        }

        super.showAtLocation(anchor, Gravity.NO_GRAVITY, centerX, y);

        int startTop = centerY - (int) (itemHeight * 0.2);
        int startBottom = centerY + (int) (itemHeight * 0.2);
        int startLeft;
        int startRight;

        if (!rtl) {
            startLeft = centerX;
            startRight = centerX + unit;
        } else {
            startLeft = centerX + width - unit;
            startRight = centerX + width;
        }

        animStartRect = new Rect(startLeft, startTop, startRight, startBottom);

        int animElevation = (int) Math.round(elevation * 0.25);

        SimpleMenuAnimation.postStartEnterAnimation(this, getBackground(),
                width, height, centerX, centerY, animStartRect, animItemHeight, animElevation, animIndex);
    }

    /**
     * Request a measurement before next show, call this when entries changed.
     */
    public void requestMeasure() {
        mRequestMeasure = true;
    }

    /**
     * Measure window width
     *
     * @param maxWidth max width for popup
     * @param entries Entries of preference hold this window
     * @return  0: skip
     *          -1: use dialog
     *          other: measuredWidth
     */
    private int measureWidth(int maxWidth, CharSequence[] entries) {
        // skip if should not measure
        if (!mRequestMeasure) {
            return 0;
        }

        mRequestMeasure = false;

        entries = Arrays.copyOf(entries, entries.length);

        Arrays.sort(entries, new Comparator<CharSequence>() {
            @Override
            public int compare(CharSequence o1, CharSequence o2) {
                return o2.length() - o1.length();
            }
        });

        Context context = getContentView().getContext();
        int width = 0;

        maxWidth = Math.min(unit * maxUnits, maxWidth);

        Rect bounds = new Rect();
        Paint textPaint = new TextPaint();
        // TODO do not hardcode
        textPaint.setTextSize(16 * context.getResources().getDisplayMetrics().scaledDensity);

        for (CharSequence chs : entries) {
            textPaint.getTextBounds(chs.toString(), 0, chs.length(), bounds);

            width = Math.max(width, bounds.width() + listPadding[POPUP_MENU][HORIZONTAL] * 2);

            // more than one line should use dialog
            if (width > maxWidth
                    || chs.toString().contains("\n")) {
                return -1;
            }
        }

        // width is a multiple of a unit
        int w = 0;
        while (width > w) {
            w += unit;
        }

        return w;
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }

    @Override
    public void showAsDropDown(View anchor) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }
}