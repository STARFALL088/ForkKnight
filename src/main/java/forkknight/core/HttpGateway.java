package forkknight.core;

import java.io.IOException;

/**
 * How the Herald actually reaches the wider realm: one plain GET.
 *
 * <p>Beacon sits behind this so its JSON reading - the interesting half -
 * can be tested with a canned answer, while the real transport is proved
 * separately against a live endpoint.
 */
public interface HttpGateway {

    /**
     * The body the given URL answers with.
     *
     * @throws IOException if the call fails or the far side answers with
     *                     anything but 2xx
     */
    String get(String url) throws IOException, InterruptedException;
}
