package com.example.sleepgame

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.sleepgame.ui.theme.SleepGameTheme
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.GodotFragment
import org.godotengine.godot.GodotHost
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

class MainActivity: AppCompatActivity(), GodotHost {
    private var godotFragment: GodotFragment? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*
        SQLiteDatabase.openDatabase(
            File(activity.filesDir, "sqlite.db").absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery("SELECT id FROM test", null).use { c ->
                var results = ""
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    results += "$id"
                }
                Log.d(TAG, results)
            }
        }
         */

        val currentGodotFragment = supportFragmentManager.findFragmentById(R.id.godot_fragment_container)
        if (currentGodotFragment is GodotFragment) {
            godotFragment = currentGodotFragment
        } else {
            godotFragment = GodotFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.godot_fragment_container, godotFragment!!)
                .commitNowAllowingStateLoss()
        }
    }

    override fun getActivity() = this

    override fun getGodot() = godotFragment?.godot

    companion object {
        val TAG = "Native"
    }
}