package dev.kartik.accessmanager.di

import javax.inject.Qualifier

/**
 * Qualifier to denote the application-level CoroutineScope.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope
