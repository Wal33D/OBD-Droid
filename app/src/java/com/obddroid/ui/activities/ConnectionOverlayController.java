package com.obddroid.ui.activities;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.obddroid.R;
import com.obddroid.services.VehicleManager;
import com.obddroid.utils.VehicleData;

import java.util.logging.Logger;

/**
 * The blocking overlay on the dashboard while the adapter connects and the vehicle is identified.
 * <p>
 * A small state machine: OFFLINE (connect prompt), CONNECTING, DECODING and FINALIZING (waiting for the
 * VIN and the decoded vehicle, with a timeout), READY (hidden) and FAILED. {@link #plan} says what each
 * state shows; the instance applies it to the views and follows {@link VehicleManager}'s events.
 */
final class ConnectionOverlayController {

    enum State {
        OFFLINE,
        CONNECTING,
        DECODING,
        FINALIZING,
        READY,
        FAILED
    }

    /** What happens to the vehicle-info timeout on entering a state. */
    enum Timeout {
        /** Stop a running timeout before changing the overlay. */
        CANCEL,
        /** (Re)start the timeout after showing the overlay. */
        START
    }

    /** What a state shows. */
    static final class Step {
        final boolean visible;
        /** Overlay title, or 0 when hidden. */
        final int titleRes;
        /** Overlay subtitle, or 0 when hidden. */
        final int subtitleRes;
        final Timeout timeout;

        Step(boolean visible, int titleRes, int subtitleRes, Timeout timeout) {
            this.visible = visible;
            this.titleRes = titleRes;
            this.subtitleRes = subtitleRes;
            this.timeout = timeout;
        }
    }

    /** Whether the dashboard footer already shows a decoded vehicle. */
    interface FooterData {
        boolean hasVehicleData();
    }

    /** Give up waiting for the vehicle after this long. */
    static final long VEHICLE_INFO_TIMEOUT_MS = 15000L;

    private final Activity activity;
    private final MainConnectionState connection;
    private final FooterData footer;
    private final Logger log;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View overlay;
    private TextView titleView;
    private TextView subtitleView;
    private VehicleManager.VehicleChangeListener vehicleListener;
    private Runnable timeoutRunnable;
    private State state = State.OFFLINE;

    ConnectionOverlayController(Activity activity, MainConnectionState connection, FooterData footer, Logger log) {
        this.activity = activity;
        this.connection = connection;
        this.footer = footer;
        this.log = log;
    }

    /**
     * What a state shows. In demo mode there is nothing to connect, so OFFLINE hides the overlay.
     */
    static Step plan(State state, boolean demoMode) {
        switch (state) {
            case OFFLINE:
                return demoMode
                        ? new Step(false, 0, 0, Timeout.CANCEL)
                        : new Step(true, R.string.connection_required_title,
                                R.string.connection_required_message, Timeout.CANCEL);
            case CONNECTING:
                return new Step(true, R.string.connection_overlay_connecting_title,
                        R.string.connection_overlay_connecting_subtitle, Timeout.CANCEL);
            case DECODING:
                return new Step(true, R.string.connection_overlay_decoding_title,
                        R.string.connection_overlay_decoding_subtitle, Timeout.START);
            case FINALIZING:
                return new Step(true, R.string.connection_overlay_finalizing_title,
                        R.string.connection_overlay_finalizing_subtitle, Timeout.START);
            case FAILED:
                return new Step(true, R.string.connection_overlay_failed_title,
                        R.string.connection_overlay_failed_subtitle, Timeout.CANCEL);
            case READY:
            default:
                return new Step(false, 0, 0, Timeout.CANCEL);
        }
    }

    /** DECODING and FINALIZING wait for the vehicle: the footer or the timeout ends them. */
    static boolean isWaitingForVehicle(State state) {
        return state == State.DECODING || state == State.FINALIZING;
    }

    /** After the VIN decoded: ready when the footer already shows it, otherwise still finalizing. */
    static State afterVehicleDecoded(boolean footerHasData) {
        return footerHasData ? State.READY : State.FINALIZING;
    }

    State getState() {
        return state;
    }

    /**
     * Find the overlay views of the current layout (again after each dashboard setContentView), follow
     * the vehicle events once, and start OFFLINE.
     */
    void bindViews() {
        overlay = activity.findViewById(R.id.connection_loading_overlay);
        titleView = activity.findViewById(R.id.connection_loading_text);
        subtitleView = activity.findViewById(R.id.connection_loading_subtext);

        if (overlay != null) {
            log.info("Connection loading overlay initialized");
        } else {
            log.warning("Could not find connection loading overlay");
        }

        if (vehicleListener == null) {
            vehicleListener = new VehicleManager.SimpleVehicleChangeListener() {
                @Override
                public void onDecodingStarted() {
                    setState(State.DECODING);
                }

                @Override
                public void onVehicleDecoded(VehicleData vehicleData) {
                    setState(afterVehicleDecoded(footer.hasVehicleData()));
                }

                @Override
                public void onDecodingError(String error) {
                    setState(State.FAILED);
                }

                @Override
                public void onVINRetrievalFailed() {
                    setState(State.FAILED);
                }

                @Override
                public void onVehicleDisconnected() {
                    setState(State.OFFLINE);
                }
            };

            VehicleManager vehicleManager = VehicleManager.getInstance();
            if (vehicleManager != null) {
                vehicleManager.addListener(vehicleListener);
            } else {
                log.warning("VehicleManager instance not available when setting up loading overlay listener");
            }
        }

        setState(State.OFFLINE);
    }

    /** The footer finished showing the vehicle: stop waiting. */
    void onFooterVehicleInfoReady() {
        if (isWaitingForVehicle(state)) {
            setState(State.READY);
        }
    }

    void setState(State newState) {
        state = newState;
        Step step = plan(newState, connection.getMode() == MainActivity.MODE.DEMO);

        if (step.timeout == Timeout.CANCEL) {
            cancelTimeout();
        }
        if (step.visible) {
            show(activity.getString(step.titleRes), activity.getString(step.subtitleRes));
        } else {
            hide();
        }
        if (step.timeout == Timeout.START) {
            startTimeout();
        }
    }

    /** Stop the timeout and the vehicle events (onDestroy). */
    void release() {
        cancelTimeout();

        if (vehicleListener != null) {
            VehicleManager vehicleManager = VehicleManager.getInstance();
            if (vehicleManager != null) {
                vehicleManager.removeListener(vehicleListener);
            }
            vehicleListener = null;
        }
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void startTimeout() {
        cancelTimeout();
        timeoutRunnable = () -> {
            timeoutRunnable = null;
            if (isWaitingForVehicle(state)) {
                log.warning("Vehicle info overlay timed out while waiting for data");
                setState(State.FAILED);
            }
        };
        handler.postDelayed(timeoutRunnable, VEHICLE_INFO_TIMEOUT_MS);
    }

    private void show(String text, String subtext) {
        activity.runOnUiThread(() -> {
            if (overlay != null) {
                if (titleView != null && text != null) {
                    titleView.setText(text);
                }
                if (subtitleView != null) {
                    subtitleView.setText(subtext != null ? subtext : "Please wait");
                }
                overlay.setVisibility(View.VISIBLE);
                log.info("Showing connection loading overlay: " + text);
            }
        });
    }

    private void hide() {
        cancelTimeout();
        activity.runOnUiThread(() -> {
            if (overlay != null) {
                overlay.setVisibility(View.GONE);
                log.info("Hiding connection loading overlay");
            }
        });
    }
}
