package com.chzzk.donation;

import java.util.concurrent.ConcurrentLinkedQueue;

public class DonationQueue {
    private static final ConcurrentLinkedQueue<DonationEvent> QUEUE = new ConcurrentLinkedQueue<>();

    public static void add(DonationEvent event) {
        QUEUE.add(event);
    }

    public static DonationEvent poll() {
        return QUEUE.poll();
    }
}
