package com.dendi.peony.service.plant;

/**
 * A stored image file served over HTTP.
 *
 * <p>The file name drives the response content type; the bytes are read from disk.
 *
 * @param filename stored file name
 * @param data file bytes
 */
public record StoredImage(String filename, byte[] data) {}
