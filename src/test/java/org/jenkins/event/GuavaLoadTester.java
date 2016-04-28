/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkins.event;

import org.junit.Assert;

import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class GuavaLoadTester {
    
    //
    // Setup
    //  - 1 channel publisher
    //  - 20,000 subscribers (varied this number):
    //  - all subscribers serializing the messages to JSON and writing to a file to
    //    mimic some event processing load
    //
    // Findings:
    //  - Setup time for registerig the subscribers was the biggest chunk of time (by far).
    //  - Publishing a message and all subscribers receiving an processing was small enough.
    //    e.g. about 300 ms to deliver to 20,000 subscribers and them all processing as
    //    described above.
    //

    public static void main(String[] args) throws IOException {
        MessageBus bus = new GuavaMessageBus();
        final Writer testWriter = new FileWriter("./target/guava-load-write.txt", false);
        
        try {
            // publisher...
            ChannelPublisher publisher = bus.publisher("channel.a");

            // subscribers...
            List<MockSubscriber> subscribers = new ArrayList<>();
            for (int i = 0; i < 20000; i++) {
                MockSubscriber subscriber = new MockSubscriber() {
                    @Override
                    public void onMessage(@Nonnull Message message) {
                        // Add the overhead of writing to a stream. It's just a local file, but...
                        try {
                            message.toJSON(testWriter);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        super.onMessage(message);
                    }
                };
                subscribers.add(subscriber);
                bus.subscribe("channel.a", subscriber, null, null);
            }

            System.out.println("starting...");
            long start = System.currentTimeMillis();
            publisher.publish(new SimpleMessage().set("a", "aVal"));
            System.out.println("finished");
            System.out.println("Took: " + (System.currentTimeMillis() - start) + " ms");

            for (MockSubscriber subscriber : subscribers) {
                Assert.assertEquals(1, subscriber.messages.size());
            }
        } finally {
            testWriter.close();
        }
    }
}
