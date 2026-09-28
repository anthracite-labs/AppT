package dev.anthracite.appt.testing

/**
 * DataStore rejects a preferences file whose name does not end in `.preferences_pb`, and throws
 * `IllegalStateException` from its factory when it does not. Tests that build their own store over
 * a temporary file therefore need the same suffix the production file has.
 */
const val PREFERENCES_FILE_NAME = "preferences.preferences_pb"
