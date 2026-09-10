package com.beatnova.app

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth

/** Compatibility bridge: MainActivity can use supabase.auth without importing the extension. */
val SupabaseClient.auth: Auth
    get() = pluginManager.getPlugin(Auth)
