package com.alananasss.kittytune.ui.profile.integrations

/**
 * The client ids the official Qobuz app registers its social sign-ins with.
 *
 * The provider redirects back to a URI that belongs to the client id which
 * started the flow, so the redirect only completes when the same client id is
 * used. Registering KittyTune's own ids (Google Cloud console, Meta developer
 * app) and putting them here is the clean way to run this without the official
 * app's ids.
 */
object QobuzSocialIds {
    const val GOOGLE_CLIENT_ID = "1054464321085-6128809nco2cvu09rj2r8cfacg703d44.apps.googleusercontent.com"
    const val FACEBOOK_APP_ID = "103352876679"
    const val FACEBOOK_REDIRECT = "fbconnect://cct.com.qobuz.music"
}
