package com.github.libretube.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.github.libretube.R
import com.github.libretube.api.JsonHelper
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.Login
import com.github.libretube.api.obj.Token
import com.github.libretube.constants.IntentData
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.databinding.DialogLoginBinding
import com.github.libretube.extensions.TAG
import com.github.libretube.extensions.toastFromMainDispatcher
import com.github.libretube.helpers.ContextHelper
import com.github.libretube.helpers.IntentHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.base.BaseActivity
import com.github.libretube.ui.preferences.InstanceSettings.Companion.INSTANCE_DIALOG_REQUEST_KEY
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LoginDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogLoginBinding.inflate(layoutInflater)

        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.login)
            .setPositiveButton(R.string.login, null)
            .setNegativeButton(R.string.register, null)
            .setView(binding.root)

        val oidcProviders = PreferenceHelper.getStringSet(PreferenceKeys.INSTANCE_OIDC_PROVIDERS, setOf())
        if (oidcProviders.isNotEmpty()) {
            dialogBuilder.setNeutralButton(R.string.oidc, null)
        }

        return dialogBuilder
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val username = binding.username.text?.toString()
                    val password = binding.password.text?.toString()

                    if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                        signIn(username, password)
                    } else {
                        Toast.makeText(context, R.string.empty, Toast.LENGTH_SHORT).show()
                    }
                }
                getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                    val username = binding.username.text?.toString().orEmpty()
                    val password = binding.password.text?.toString().orEmpty()

                    if (isEmail(username)) {
                        showPrivacyAlertDialog(username, password)
                    } else if (username.isNotEmpty() && password.isNotEmpty()) {
                        signIn(username, password, true)
                    } else {
                        Toast.makeText(context, R.string.empty, Toast.LENGTH_SHORT).show()
                    }
                }
                getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
                    showOidcProviderDialog(oidcProviders.toList())
                }
            }
    }

    private fun signIn(username: String, password: String, createNewAccount: Boolean = false) {
        val login = Login(username, password)
        lifecycleScope.launch(Dispatchers.IO) {
            val response = try {
                if (createNewAccount) {
                    RetrofitInstance.authApi.register(login)
                } else {
                    RetrofitInstance.authApi.login(login)
                }
            } catch (e: HttpException) {
                val errorMessage = e.response()?.errorBody()?.string()?.runCatching {
                    JsonHelper.json.decodeFromString<Token>(this).error
                }?.getOrNull() ?: context?.getString(R.string.server_error).orEmpty()
                context?.toastFromMainDispatcher(errorMessage)
                return@launch
            } catch (e: Exception) {
                Log.e(TAG(), e.toString())
                context?.toastFromMainDispatcher(e.localizedMessage.orEmpty())
                return@launch
            }

            if (response.error != null) {
                context?.toastFromMainDispatcher(response.error)
                return@launch
            }
            if (response.token == null) return@launch

            context?.toastFromMainDispatcher(
                if (createNewAccount) R.string.registered else R.string.loggedIn
            )

            PreferenceHelper.setToken(response.token, false)
            PreferenceHelper.setUsername(login.username)

            withContext(Dispatchers.Main) {
                setFragmentResult(
                    INSTANCE_DIALOG_REQUEST_KEY,
                    bundleOf(IntentData.loginTask to true)
                )
            }
            dialog?.dismiss()
        }
    }

    private fun showPrivacyAlertDialog(email: String, password: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.privacy_alert)
            .setMessage(R.string.username_email)
            .setNegativeButton(R.string.proceed) { _, _ ->
                signIn(email, password, true)
            }
            .setPositiveButton(R.string.cancel, null)
            .show()
    }

    private fun showOidcProviderDialog(oidcProviders: List<String>) {
        var selectedProviderIndex = 0

        val appContext = requireContext().applicationContext
        val fragmentManager = ContextHelper.unwrapActivity<BaseActivity>(requireContext())
            .supportFragmentManager
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.oidc_login)
            .setSingleChoiceItems(oidcProviders.toTypedArray(), selectedProviderIndex) { _, selected ->
                selectedProviderIndex = selected
            }
            .setPositiveButton(R.string.login) { _, _ ->
                val provider = oidcProviders[selectedProviderIndex]
                val redirectUrl = URLEncoder.encode("${appContext.packageName}://callback", StandardCharsets.UTF_8)
                val oidcUrl = "${RetrofitInstance.authUrl}/oidc/${provider}/login?redirect=${redirectUrl}"
                IntentHelper.openLinkFromHref(appContext, fragmentManager, oidcUrl, forceDefaultOpen = true)

                this@LoginDialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isEmail(text: String): Boolean {
        return Patterns.EMAIL_ADDRESS.toRegex().matches(text)
    }
}
