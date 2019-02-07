package com.google.android.systemui.elmyra;

import android.content.Context;
import android.metrics.LogMaker;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.Dumpable;
import com.google.android.systemui.elmyra.actions.Action;
import com.google.android.systemui.elmyra.actions.Action.Listener;
import com.google.android.systemui.elmyra.feedback.FeedbackEffect;
import com.google.android.systemui.elmyra.gates.Gate;
import com.google.android.systemui.elmyra.sensors.GestureSensor;
import com.google.android.systemui.elmyra.sensors.GestureSensor.DetectionProperties;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ElmyraService implements Dumpable {
    protected final Listener mActionListener = new C15821();
    private final List<Action> mActions;
    private final Context mContext;
    private final List<FeedbackEffect> mFeedbackEffects;
    protected final Gate.Listener mGateListener = new C15832();
    private final List<Gate> mGates;
    private final GestureSensor.Listener mGestureListener = new GestureListener(this, null);
    private final GestureSensor mGestureSensor;
    private Action mLastActiveAction;
    private long mLastPrimedGesture;
    private int mLastStage;
    private final MetricsLogger mLogger;
    private final PowerManager mPowerManager;
    private final WakeLock mWakeLock;

    /* renamed from: com.google.android.systemui.elmyra.ElmyraService$1 */
    class C15821 implements Listener {
        C15821() {
        }

        @Override
        public void onActionAvailabilityChanged(Action action) {
            updateSensorListener();
        }
    }

    /* renamed from: com.google.android.systemui.elmyra.ElmyraService$2 */
    class C15832 implements Gate.Listener {
        C15832() {
        }

        @Override
        public void onGateChanged(Gate gate) {
            updateSensorListener();
        }
    }

    private class GestureListener implements GestureSensor.Listener {
        private GestureListener() {
        }

        GestureListener(ElmyraService elmyraService, C15821 c15821) {
            this();
        }

        public void onGestureDetected(GestureSensor gestureSensor, DetectionProperties detectionProperties) {
            mWakeLock.acquire(2000);
            boolean isInteractive = mPowerManager.isInteractive();
            int i = (detectionProperties == null || !detectionProperties.isHostSuspended()) ? !isInteractive ? 2 : 1 : 3;
            LogMaker latency = new LogMaker(999).setType(4).setSubtype(i).setLatency(isInteractive ? SystemClock.uptimeMillis() - mLastPrimedGesture : 0);
            mLastPrimedGesture = 0;
            Action access$100 = updateActiveAction();
            if (access$100 != null) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Triggering ");
                stringBuilder.append(access$100);
                Log.i("Elmyra/ElmyraService", stringBuilder.toString());
                access$100.onTrigger(detectionProperties);
                i = 0;
                while (true) {
                    int i2 = i;
                    if (i2 >= mFeedbackEffects.size()) {
                        break;
                    }
                    mFeedbackEffects.get(i2).onResolve(detectionProperties);
                    i = i2 + 1;
                }
                latency.setPackageName(access$100.getClass().getName());
            }
            mLogger.write(latency);
        }

        public void onGestureProgress(GestureSensor gestureSensor, float f, int i) {
            Action access$100 = updateActiveAction();
            if (access$100 != null) {
                access$100.onProgress(f, i);
                int i2 = 0;
                while (true) {
                    int i3 = i2;
                    if (i3 >= mFeedbackEffects.size()) {
                        break;
                    }
                    mFeedbackEffects.get(i3).onProgress(f, i);
                    i2 = i3 + 1;
                }
            }
            if (i != mLastStage) {
                long uptimeMillis = SystemClock.uptimeMillis();
                if (i == 2) {
                    mLogger.action(998);
                    mLastPrimedGesture = uptimeMillis;
                } else if (i == 0 && mLastPrimedGesture != 0) {
                    mLogger.write(new LogMaker(997).setType(4).setLatency(uptimeMillis - mLastPrimedGesture));
                }
                mLastStage = i;
            }
        }
    }

    public ElmyraService(Context context, ServiceConfiguration serviceConfiguration) {
        mContext = context;
        mLogger = new MetricsLogger();
        mPowerManager = (PowerManager) mContext.getSystemService("power");
        mWakeLock = mPowerManager.newWakeLock(1, "Elmyra/ElmyraService");

        // Anonymous Consumer class for Actions
        Consumer<Action> setActionListener = new Consumer<Action>() {
            public void accept(Action action) {
                action.setListener(mActionListener);
            }
        };

        mActions = new ArrayList(serviceConfiguration.getActions());
        mActions.forEach(setActionListener);
        mFeedbackEffects = new ArrayList(serviceConfiguration.getFeedbackEffects());

        // Anonymous Consumer class for Gates
        Consumer<Gate> setGateListener = new Consumer<Gate>() {
            public void accept(Gate gate) {
                gate.setListener(mGateListener);
            }
        };

        mGates = new ArrayList(serviceConfiguration.getGates());
        mGates.forEach(setGateListener);
        mGestureSensor = serviceConfiguration.getGestureSensor();
        if (mGestureSensor != null) {
            mGestureSensor.setGestureListener(mGestureListener);
        }
        updateSensorListener();
    }

    private void activateGates() {
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < mGates.size()) {
                mGates.get(i2).activate();
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    private Gate blockingGate() {
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 >= mGates.size()) {
                return null;
            }
            if (mGates.get(i2).isBlocking()) {
                return (Gate) mGates.get(i2);
            }
            i = i2 + 1;
        }
    }

    private void deactivateGates() {
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < mGates.size()) {
                mGates.get(i2).deactivate();
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    private Action firstAvailableAction() {
        // int i = 0;
        // while (true) {
        //     int i2 = i;
        //     if (i2 >= mActions.size()) {
        //         return null;
        //     }
        //     if (((Action) mActions.get(i2)).isAvailable()) {
        //         return (Action) mActions.get(i2);
        //     }
        //     i = i2 + 1;
        // }
        return mActions.get(0);
    }

    private void startListening() {
        if (mGestureSensor != null && !mGestureSensor.isListening()) {
            mGestureSensor.startListening();
        }
    }

    private void stopListening() {
        if (mGestureSensor != null && mGestureSensor.isListening()) {
            mGestureSensor.stopListening();
            for (int i = 0; i < mFeedbackEffects.size(); i++) {
                mFeedbackEffects.get(i).onRelease();
            }
            Action updateActiveAction = updateActiveAction();
            if (updateActiveAction != null) {
                updateActiveAction.onProgress(0.0f, 0);
            }
        }
    }

    private Action updateActiveAction() {
        Action firstAvailableAction = firstAvailableAction();
        if (!(mLastActiveAction == null || firstAvailableAction == mLastActiveAction)) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Switching action from ");
            stringBuilder.append(mLastActiveAction);
            stringBuilder.append(" to ");
            stringBuilder.append(firstAvailableAction);
            Log.i("Elmyra/ElmyraService", stringBuilder.toString());
            mLastActiveAction.onProgress(0.0f, 0);
        }
        mLastActiveAction = firstAvailableAction;
        return firstAvailableAction;
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        int i;
        int i2 = 0;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ElmyraService.class.getSimpleName());
        stringBuilder.append(" state:");
        printWriter.println(stringBuilder.toString());
        printWriter.println("  Gates:");
        for (i = 0; i < mGates.size(); i++) {
            printWriter.print("    ");
            if (mGates.get(i).isActive()) {
                printWriter.print(mGates.get(i).isBlocking() ? "X " : "O ");
            } else {
                printWriter.print("- ");
            }
            printWriter.println(mGates.get(i).toString());
        }
        printWriter.println("  Actions:");
        for (i = 0; i < mActions.size(); i++) {
            printWriter.print("    ");
            printWriter.print(mActions.get(i).isAvailable() ? "O " : "X ");
            printWriter.println(mActions.get(i).toString());
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append("  Active: ");
        stringBuilder.append(mLastActiveAction);
        printWriter.println(stringBuilder.toString());
        printWriter.println("  Feedback Effects:");
        while (i2 < mFeedbackEffects.size()) {
            printWriter.print("    ");
            printWriter.println(mFeedbackEffects.get(i2).toString());
            i2++;
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append("  Gesture Sensor: ");
        stringBuilder.append(mGestureSensor.toString());
        printWriter.println(stringBuilder.toString());
        if (mGestureSensor instanceof Dumpable) {
            ((Dumpable) mGestureSensor).dump(fileDescriptor, printWriter, strArr);

        }
    }

    protected void updateSensorListener() {
        Action updateActiveAction = updateActiveAction();
        if (updateActiveAction == null) {
            Log.i("Elmyra/ElmyraService", "No available actions");
            deactivateGates();
            stopListening();
            return;
        }
        activateGates();
        Gate blockingGate = blockingGate();
        if (blockingGate != null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Gated by ");
            stringBuilder.append(blockingGate);
            Log.i("Elmyra/ElmyraService", stringBuilder.toString());
            stopListening();
            return;
        }
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append("Unblocked; current action: ");
        stringBuilder2.append(updateActiveAction);
        Log.i("Elmyra/ElmyraService", stringBuilder2.toString());
        startListening();
    }
}