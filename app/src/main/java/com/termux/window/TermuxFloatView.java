package com.termux.window;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.termux.window.settings.properties.TermuxFloatAppSharedProperties;

public class TermuxFloatView extends LinearLayout {

    public static final float ALPHA_FOCUS = 0.95f;
    public static final float ALPHA_NOT_FOCUS = 0.8f;
    public static final float ALPHA_MOVING = 0.65f;

    private int DISPLAY_WIDTH, DISPLAY_HEIGHT;

    final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
    WindowManager mWindowManager;

    private TerminalView mTerminalView;
    ViewGroup mWindowControls;
    FloatingBubbleManager mFloatingBubbleManager;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxFloatView}.
     */
    TermuxFloatViewClient mTermuxFloatViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxFloatService}.
     */
    TermuxFloatSessionClient mTermuxFloatSessionClient;

    /**
     * Termux Float app shared preferences manager.
     */
    private TermuxFloatAppSharedPreferences mPreferences;

    /**
     * Termux app shared properties manager, loaded from termux.properties
     */
    private TermuxFloatAppSharedProperties mProperties;

    private boolean withFocus = true;

    final int[] location = new int[2];
    final int[] windowControlsLocation = new int[2];

    private static final String LOG_TAG = "TermuxFloatView";

    public TermuxFloatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(ALPHA_FOCUS);
        setupGlassOutline();
    }

    public void setupGlassOutline() {
        final float cornerRadiusPx = ViewUtils.dpToPx(getContext(), 20);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        });
        setClipToOutline(true);
    }

    private static int computeLayoutFlags(boolean withFocus) {
        if (withFocus) {
            return 0;
        } else {
            return WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
    }

    public void initFloatView(TermuxFloatService service) {
        Logger.logDebug(LOG_TAG, "initFloatView");

        // Load termux shared properties
        mProperties = new TermuxFloatAppSharedProperties(getContext());

        // Load termux float shared preferences
        mPreferences = TermuxFloatAppSharedPreferences.build(getContext(), true);
        if (mPreferences == null) {
            return;
        }

        mTermuxFloatSessionClient = new TermuxFloatSessionClient(service, this);

        mTerminalView = findViewById(R.id.terminal_view);
        mTermuxFloatViewClient = new TermuxFloatViewClient(this, mTermuxFloatSessionClient);
        mTerminalView.setTerminalViewClient(mTermuxFloatViewClient);
        mTermuxFloatViewClient.initFloatView();

        mFloatingBubbleManager = new FloatingBubbleManager(this);
        initWindowControls();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initWindowControls() {
        mWindowControls = findViewById(R.id.window_controls);

        // Header Touch Listener for moving the floating window
        mWindowControls.setOnTouchListener(new OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        changeFocus(true);
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        setAlpha(ALPHA_MOVING);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        layoutParams.x = Math.max(0, Math.min(initialX + deltaX, DISPLAY_WIDTH - layoutParams.width));
                        layoutParams.y = Math.max(0, Math.min(initialY + deltaY, DISPLAY_HEIGHT - layoutParams.height));
                        if (getWindowToken() != null)
                            mWindowManager.updateViewLayout(TermuxFloatView.this, layoutParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        setAlpha(withFocus ? ALPHA_FOCUS : ALPHA_NOT_FOCUS);
                        if (mPreferences != null) {
                            mPreferences.setWindowX(layoutParams.x);
                            mPreferences.setWindowY(layoutParams.y);
                        }
                        return true;
                }
                return false;
            }
        });

        // Sol alt köşedeki boyutlandırma butonu (Bottom-left resize handle)
        View resizeButton = findViewById(R.id.resize_button_left);
        if (resizeButton != null) {
            resizeButton.setOnTouchListener(new OnTouchListener() {
                private int initialX, initialY, initialWidth, initialHeight;
                private float initialTouchX, initialTouchY;
                private final int MIN_WIDTH = (int) ViewUtils.dpToPx(getContext(), 160);
                private final int MIN_HEIGHT = (int) ViewUtils.dpToPx(getContext(), 120);

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            changeFocus(true);
                            initialX = layoutParams.x;
                            initialY = layoutParams.y;
                            initialWidth = layoutParams.width;
                            initialHeight = layoutParams.height;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            setAlpha(ALPHA_MOVING);
                            setBackgroundResource(R.drawable.floating_window_background_resize);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            int deltaX = (int) (event.getRawX() - initialTouchX);
                            int deltaY = (int) (event.getRawY() - initialTouchY);

                            // Sol alt köşeden çekerken: sola çekmek (deltaX < 0) genişliği artırır, X'i sola kaydırır
                            int newWidth = initialWidth - deltaX;
                            int newHeight = initialHeight + deltaY;

                            newWidth = Math.max(MIN_WIDTH, Math.min(newWidth, DISPLAY_WIDTH));
                            newHeight = Math.max(MIN_HEIGHT, Math.min(newHeight, DISPLAY_HEIGHT));

                            int newX = initialX + (initialWidth - newWidth);
                            newX = Math.max(0, Math.min(newX, DISPLAY_WIDTH - newWidth));

                            layoutParams.x = newX;
                            layoutParams.width = newWidth;
                            layoutParams.height = newHeight;

                            if (getWindowToken() != null)
                                mWindowManager.updateViewLayout(TermuxFloatView.this, layoutParams);
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            setBackgroundResource(R.drawable.floating_window_background);
                            setAlpha(withFocus ? ALPHA_FOCUS : ALPHA_NOT_FOCUS);
                            if (mPreferences != null) {
                                mPreferences.setWindowX(layoutParams.x);
                                mPreferences.setWindowY(layoutParams.y);
                                mPreferences.setWindowWidth(layoutParams.width);
                                mPreferences.setWindowHeight(layoutParams.height);
                            }
                            return true;
                    }
                    return false;
                }
            });
        }

        // Klavye Aç/Kapa Butonu
        ImageButton keyboardButton = findViewById(R.id.keyboard_button);
        if (keyboardButton != null) {
            keyboardButton.setOnClickListener(v -> KeyboardUtils.toggleSoftKeyboard(getContext()));
        }

        // Minimize Butonu
        ImageButton minimizeButton = findViewById(R.id.minimize_button);
        if (minimizeButton != null) {
            minimizeButton.setOnClickListener(v -> mFloatingBubbleManager.toggleBubble());
        }

        // Exit Butonu
        ImageButton exitButton = findViewById(R.id.exit_button);
        if (exitButton != null) {
            exitButton.setOnClickListener(v -> exit());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        Point displaySize = new Point();
        getDisplay().getSize(displaySize);
        DISPLAY_WIDTH = displaySize.x;
        DISPLAY_HEIGHT = displaySize.y;

        if (mTermuxFloatSessionClient != null)
            mTermuxFloatSessionClient.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mTermuxFloatSessionClient != null)
            mTermuxFloatSessionClient.onDetachedFromWindow();
    }

    @SuppressLint("RtlHardcoded")
    public void launchFloatingWindow() {
        int widthAndHeight = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.flags = computeLayoutFlags(true);
        layoutParams.width = widthAndHeight;
        layoutParams.height = widthAndHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;

        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        if (mPreferences != null) {
            layoutParams.x = mPreferences.getWindowX();
            layoutParams.y = mPreferences.getWindowY();
            layoutParams.width = mPreferences.getWindowWidth();
            layoutParams.height = mPreferences.getWindowHeight();
        }

        mWindowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        if (getWindowToken() == null)
            mWindowManager.addView(this, layoutParams);
        showTouchKeyboard();
    }

    /**
     * Intercept touch events to manage window focus without interfering with text selection.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        float touchX = event.getRawX();
        float touchY = event.getRawY();

        boolean clickedInside = (touchX >= x) && (touchX <= (x + layoutParams.width)) && (touchY >= y) && (touchY <= (y + layoutParams.height));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!clickedInside) {
                    changeFocus(false);
                } else if (!withFocus) {
                    changeFocus(true);
                }
                break;
        }
        return false;
    }

    void showTouchKeyboard() {
        mTerminalView.post(() -> KeyboardUtils.showSoftKeyboard(getContext(), mTerminalView));
    }

    void hideTouchKeyboard() {
        mTerminalView.post(() -> KeyboardUtils.hideSoftKeyboard(getContext(), mTerminalView));
    }

    /**
     * Visually indicate focus and show the soft input as needed.
     */
    void changeFocus(boolean newFocus) {
        if (newFocus && mFloatingBubbleManager != null && mFloatingBubbleManager.isMinimized()) {
            mFloatingBubbleManager.displayAsFloatingWindow();
        }
        if (newFocus == withFocus) {
            if (newFocus) showTouchKeyboard();
            return;
        }
        withFocus = newFocus;
        layoutParams.flags = computeLayoutFlags(withFocus);
        if (getWindowToken() != null)
            mWindowManager.updateViewLayout(this, layoutParams);
        setAlpha(newFocus ? ALPHA_FOCUS : ALPHA_NOT_FOCUS);
    }

    public void closeFloatingWindow() {
        if (getWindowToken() != null)
            mWindowManager.removeView(this);

        if (mFloatingBubbleManager != null) {
            mFloatingBubbleManager.cleanup();
            mFloatingBubbleManager = null;
        }
    }

    private void exit() {
        Intent exitIntent = new Intent(getContext(), TermuxFloatService.class).setAction(TermuxConstants.TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE.ACTION_STOP_SERVICE);
        getContext().startService(exitIntent);
    }

    public boolean isVisible() {
        return isAttachedToWindow() && isShown();
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxFloatViewClient getTermuxFloatViewClient() {
        return mTermuxFloatViewClient;
    }

    public TermuxFloatSessionClient getTermuxFloatSessionClient() {
        return mTermuxFloatSessionClient;
    }

    public TermuxFloatAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxFloatAppSharedProperties getProperties() {
        return mProperties;
    }

    public void reloadViewStyling() {
        if (mTermuxFloatSessionClient != null)
            mTermuxFloatSessionClient.onReload();
    }
}
