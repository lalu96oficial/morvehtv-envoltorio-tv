package com.morvehtv.envoltorio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Pantalla de inicio: lo primero que ve el usuario al abrir la app.
 * Entrar y Crear cuenta abren la biblioteca en su login y su registro
 * (usuario y contraseña). Salir cierra la aplicación.
 */
class InicioActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(64), dp(48), dp(64), dp(48))
        }

        raiz.addView(TextView(this).apply {
            text = "MorvehTV"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            gravity = Gravity.CENTER
        })
        raiz.addView(TextView(this).apply {
            text = "Elige una opción"
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(32))
        })

        val entrar = boton("Entrar") { abrir("https://tv.morvehtv.com/#/entrar") }
        raiz.addView(entrar)
        raiz.addView(boton("Crear cuenta") { abrir("https://tv.morvehtv.com/#/registro") })
        raiz.addView(boton("Web morvehtv.com") { abrir(BuildConfig.SITE_URL) })
        raiz.addView(boton("Salir") { finish() })

        setContentView(raiz)
        entrar.requestFocus()
    }

    private fun boton(texto: String, accion: () -> Unit): Button =
        Button(this).apply {
            text = texto
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { accion() }
            layoutParams = LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
        }

    private fun abrir(url: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_URL, url))
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), resources.displayMetrics).toInt()
}
