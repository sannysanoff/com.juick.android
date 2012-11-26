package com.juickadvanced;

/**
 * This service is needed because GCM derives service class name from application package name.
 * todo: maybe it is possible to override?
 */
public class GCMIntentService extends com.juick.android.GCMIntentService {
}
