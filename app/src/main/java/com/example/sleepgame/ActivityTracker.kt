package com.example.sleepgame

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadLocalRandom.current
import kotlin.text.get

private class ActivityTrackerImpl<T: Activity> : Application.ActivityLifecycleCallbacks {
    var current: WeakReference<T>? = null

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity as T)
    }
    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}

open class ActivityTracker<T: Activity> {
    private var impl = ActivityTrackerImpl<T>()

    fun attach(activity: T) {
        activity.registerActivityLifecycleCallbacks(impl)
    }

    val resumedActivity: T? get() = impl.current?.get()
}