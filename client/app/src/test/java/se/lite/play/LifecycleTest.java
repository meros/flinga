package se.lite.play;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;

/**
 * Found on the real TV (2026-10-16): leaving the app with Back destroys the activity and shuts its executor
 * down, but the process lives on and timers the activity had already posted (the 5-minute now/next refresh)
 * fire later and call the dead executor: RejectedExecutionException, process dies, sometimes seconds after
 * the user started the app again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LifecycleTest {

    @Test
    public void timersOfADestroyedActivityDoNotCrashTheProcess() throws Exception {
        Fixtures.install();
        MainActivity.renderOnly = true;
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture")
                .putString("accessToken", "fixture")
                .putString("refreshToken", "fixture")
                .commit();

        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        // Let start() finish on the executor so the 5-minute refresh timer is posted.
        for (int i = 0; i < 40; i++) {
            Thread.sleep(25);
            shadowOf(Looper.getMainLooper()).idle();
        }
        c.pause().stop().destroy();

        // Ten minutes pass in the same process: every timer the dead activity left behind fires.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));
    }
}
