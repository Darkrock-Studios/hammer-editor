package de.jonasbroeckmann.kzip

import kotlinx.io.IOException

/**
 * An exception thrown when an error occurs while working with ZIP files.
 *
 * @param message the message of the exception
 */
public class ZipException(message: String) : IOException(message)
